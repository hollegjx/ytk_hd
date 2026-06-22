package learn.gjx.tuku1.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.manager.FileManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.dto.picture.PictureEditRequest;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRequest;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRule;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.dto.picture.PictureReviewRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadByBatchRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadByUrlRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUpdateRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link PictureService} 的具体实现，依托 MyBatis-Plus 提供 CRUD 与业务扩展。
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    private static final int DEFAULT_BATCH_UPLOAD_COUNT = 10;

    private static final int MAX_BATCH_UPLOAD_COUNT = 30;

    private static final String BING_IMAGE_SEARCH_URL = "https://cn.bing.com/images/async?q=%s&mmasync=1";

    private static final String BING_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. 校验必要参数
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "File cannot be empty");
        }

        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        Picture oldPicture = getAuthorizedOldPicture(pictureId, loginUser);

        // 2. 上传图片到对象存储
        String uploadPathPrefix = getUploadPathPrefix(oldPicture, loginUser);
        PictureUploadResult uploadResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        return saveUploadedPicture(uploadResult, oldPicture, pictureId,
                pictureUploadRequest == null ? null : pictureUploadRequest.getName(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getIntroduction(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getCategory(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getTags(),
                null,
                loginUser);
    }

    @Override
    public PictureVO uploadPictureByUrl(PictureUploadByUrlRequest pictureUploadByUrlRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (pictureUploadByUrlRequest == null || StrUtil.isBlank(pictureUploadByUrlRequest.getFileUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不能为空");
        }
        Long pictureId = pictureUploadByUrlRequest.getId();
        Picture oldPicture = getAuthorizedOldPicture(pictureId, loginUser);

        String uploadPathPrefix = getUploadPathPrefix(oldPicture, loginUser);
        PictureUploadResult uploadResult = fileManager.uploadPictureByUrl(pictureUploadByUrlRequest.getFileUrl(), uploadPathPrefix);

        return saveUploadedPicture(uploadResult, oldPicture, pictureId,
                pictureUploadByUrlRequest.getName(),
                pictureUploadByUrlRequest.getIntroduction(),
                pictureUploadByUrlRequest.getCategory(),
                pictureUploadByUrlRequest.getTags(),
                pictureUploadByUrlRequest.getFileUrl(),
                loginUser);
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可批量导入图片");
        }
        if (pictureUploadByBatchRequest == null || StrUtil.isBlank(pictureUploadByBatchRequest.getSearchText())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索关键词不能为空");
        }
        String searchText = pictureUploadByBatchRequest.getSearchText().trim();
        Integer count = pictureUploadByBatchRequest.getCount();
        if (count == null) {
            count = DEFAULT_BATCH_UPLOAD_COUNT;
        }
        if (count <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "导入数量必须大于 0");
        }
        if (count > MAX_BATCH_UPLOAD_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "导入数量不能超过 30");
        }
        Integer offset = pictureUploadByBatchRequest.getOffset();
        if (offset == null) {
            offset = 0;
        }
        if (offset < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "偏移量不能小于 0");
        }

        String searchUrl = String.format(BING_IMAGE_SEARCH_URL, encodeSearchText(searchText));
        Document document;
        try {
            document = Jsoup.connect(searchUrl)
                    .userAgent(BING_USER_AGENT)
                    .referrer("https://cn.bing.com/images/search")
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            log.error("获取 Bing 图片搜索结果失败，searchText: {}", searchText, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片搜索结果失败");
        }

        Element dgControl = document.getElementsByClass("dgControl").first();
        if (dgControl == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片搜索结果");
        }
        Elements imageElements = dgControl.select("img.mimg");
        if (CollUtil.isEmpty(imageElements)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片搜索结果");
        }

        Set<String> candidateUrlSet = new LinkedHashSet<>();
        for (Element imageElement : imageElements) {
            String imageUrl = normalizeImageUrl(imageElement.attr("src"));
            if (imageUrl != null) {
                candidateUrlSet.add(imageUrl);
            }
        }
        List<String> candidateUrlList = new ArrayList<>(candidateUrlSet);
        if (offset >= candidateUrlList.size()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "偏移量超过可抓取图片数量");
        }

        int successCount = 0;
        for (int i = offset; i < candidateUrlList.size(); i++) {
            if (successCount >= count) {
                break;
            }
            String imageUrl = candidateUrlList.get(i);
            PictureUploadByUrlRequest uploadByUrlRequest = new PictureUploadByUrlRequest();
            uploadByUrlRequest.setFileUrl(imageUrl);
            uploadByUrlRequest.setName(searchText + "-" + (i + 1));
            uploadByUrlRequest.setCategory(pictureUploadByBatchRequest.getCategory());
            uploadByUrlRequest.setTags(pictureUploadByBatchRequest.getTags());
            try {
                uploadPictureByUrl(uploadByUrlRequest, loginUser);
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入单张图片失败，searchText: {}, fileUrl: {}", searchText, imageUrl, e);
            }
        }

        if (successCount == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量导入失败");
        }
        return successCount;
    }

    private String encodeSearchText(String searchText) {
        try {
            return URLEncoder.encode(searchText, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("搜索关键词编码失败，searchText: {}", searchText, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "搜索关键词编码失败");
        }
    }

    private String normalizeImageUrl(String imageUrl) {
        if (StrUtil.isBlank(imageUrl)) {
            return null;
        }
        int queryIndex = imageUrl.indexOf("?");
        if (queryIndex >= 0) {
            imageUrl = imageUrl.substring(0, queryIndex);
        }
        imageUrl = imageUrl.trim();
        String lowerUrl = imageUrl.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return null;
        }
        return imageUrl;
    }

    private PictureVO saveUploadedPicture(PictureUploadResult uploadResult, Picture oldPicture, Long pictureId,
                                          String name, String introduction, String category, List<String> tags,
                                          String sourceUrl, User loginUser) {
        String normalizedSourceUrl = normalizeSourceUrl(sourceUrl);
        Picture picture = new Picture();
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        picture.setUrl(uploadResult.getUrl());
        picture.setPicSize(uploadResult.getPicSize());
        picture.setPicWidth(uploadResult.getPicWidth());
        picture.setPicHeight(uploadResult.getPicHeight());
        picture.setPicScale(uploadResult.getPicScale());
        picture.setPicFormat(uploadResult.getPicFormat());
        picture.setUserId(oldPicture == null ? loginUser.getId() : oldPicture.getUserId());
        if (normalizedSourceUrl != null) {
            picture.setSourceUrl(normalizedSourceUrl);
        }

        picture.setName(StrUtil.blankToDefault(name, uploadResult.getPicName()));
        picture.setIntroduction(introduction);
        picture.setCategory(category);
        if (CollUtil.isNotEmpty(tags)) {
            picture.setTags(JSONUtil.toJsonStr(tags));
        }

        if (userService.isAdmin(loginUser)) {
            fillReviewPassParams(picture, loginUser, "管理员上传自动通过");
        } else {
            fillReviewingParams(picture);
        }

        boolean result = this.saveOrUpdate(picture);
        if (!result) {
            log.error("Failed to save picture, pictureId: {}", pictureId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Failed to save picture");
        }
        if (pictureId != null && normalizedSourceUrl == null) {
            clearSourceUrl(picture.getId());
        }
        if (PictureReviewStatusEnum.REVIEWING.getValue() == picture.getReviewStatus()) {
            clearReviewDetail(picture.getId());
        }

        return PictureVO.objToVo(this.getById(picture.getId()));
    }

    private String normalizeSourceUrl(String sourceUrl) {
        if (StrUtil.isBlank(sourceUrl)) {
            return null;
        }
        String trimmedSourceUrl = sourceUrl.trim();
        if (trimmedSourceUrl.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Source URL is too long");
        }
        return trimmedSourceUrl;
    }

    private Picture getAuthorizedOldPicture(Long pictureId, User loginUser) {
        if (pictureId == null) {
            return null;
        }
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        if (!Objects.equals(oldPicture.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限更新该图片");
        }
        return oldPicture;
    }

    private String getUploadPathPrefix(Picture oldPicture, User loginUser) {
        Long ownerId = oldPicture == null ? loginUser.getId() : oldPicture.getUserId();
        return String.format("public/%s", ownerId);
    }

    @Override
    public void validPicture(Picture picture) {
        if (picture == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture info cannot be empty");
        }

        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();

        // 更新时必须携带主键
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid picture ID");
        }
        // 访问地址需要校验长度
        if (StrUtil.isNotBlank(url) && url.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture URL is too long");
        }
        // 简介也需要长度限制
        if (StrUtil.isNotBlank(introduction) && introduction.length() > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture introduction is too long");
        }
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从请求体读取各类过滤字段
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();

        // 组装查询条件
        // 关键字匹配（名称或简介）
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }
        // 精确匹配
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(picWidth != null, "picWidth", picWidth);
        queryWrapper.eq(picHeight != null, "picHeight", picHeight);
        queryWrapper.eq(picSize != null, "picSize", picSize);
        queryWrapper.eq(picScale != null, "picScale", picScale);
        queryWrapper.eq(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(reviewStatus != null, "reviewStatus", reviewStatus);
        // 模糊匹配
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        // 标签字段包含查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), "asc".equals(sortOrder), sortField);

        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 转换成视图对象
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 补充上传用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public List<PictureVO> getPictureVOList(List<Picture> pictureList, HttpServletRequest request) {
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 实体列表转为视图对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 1) 批量查询相关用户
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(userIdSet)) {
            return pictureVOList;
        }

        // 按用户主键分组
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2) 将用户信息写入视图对象
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });

        return pictureVOList;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());

        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 批量转换
        List<PictureVO> pictureVOList = getPictureVOList(pictureList, request);
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;
    }

    @Override
    public PictureProcessResult processPicture(PictureProcessRequest pictureProcessRequest, HttpServletRequest request) {
        if (pictureProcessRequest == null || pictureProcessRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        Picture picture = this.getById(pictureProcessRequest.getPictureId());
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        List<PictureProcessRule> rules = pictureProcessRequest.getRules();
        if (CollUtil.isEmpty(rules)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请至少提供一条处理规则");
        }
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!isAdmin && !Objects.equals(picture.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权处理该图片");
        }
        String objectKey = fileManager.extractKeyFromUrl(picture.getUrl());
        if (StrUtil.isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片路径异常");
        }
        return fileManager.processPicture(objectKey, rules, pictureProcessRequest.getOutputDir());
    }

    @Override
    public boolean reviewPicture(PictureReviewRequest pictureReviewRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可审核图片");
        }
        if (pictureReviewRequest == null || pictureReviewRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        Long pictureId = pictureReviewRequest.getId();
        if (pictureId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        }
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        if (!PictureReviewStatusEnum.isReviewResult(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态只能为通过或拒绝");
        }
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        PictureReviewStatusEnum statusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        Picture picture = new Picture();
        picture.setId(pictureId);
        fillReviewResultParams(picture, loginUser, reviewStatus,
                StrUtil.blankToDefault(pictureReviewRequest.getReviewMessage(), statusEnum.getName()));

        boolean result = this.updateById(picture);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核图片失败");
        }
        return true;
    }

    @Override
    public boolean editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (pictureEditRequest == null || pictureEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        Picture picture = new Picture();
        org.springframework.beans.BeanUtils.copyProperties(pictureEditRequest, picture);
        if (pictureEditRequest.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        }
        picture.setEditTime(new Date());
        validPicture(picture);

        Long pictureId = pictureEditRequest.getId();
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!Objects.equals(oldPicture.getUserId(), loginUser.getId()) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限编辑");
        }
        if (isAdmin) {
            fillReviewPassParams(picture, loginUser, "管理员编辑自动通过");
        } else {
            fillReviewingParams(picture);
        }
        boolean result = this.updateById(picture);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "编辑失败");
        }
        if (!isAdmin) {
            clearReviewDetail(pictureId);
        }
        return true;
    }

    @Override
    public boolean updatePicture(PictureUpdateRequest pictureUpdateRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可更新图片");
        }
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        Picture picture = new Picture();
        org.springframework.beans.BeanUtils.copyProperties(pictureUpdateRequest, picture);
        if (pictureUpdateRequest.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        }
        validPicture(picture);

        Long pictureId = pictureUpdateRequest.getId();
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        fillReviewPassParams(picture, loginUser, "管理员更新自动通过");
        boolean result = this.updateById(picture);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新失败");
        }
        return true;
    }

    private void fillReviewingParams(Picture picture) {
        picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        picture.setReviewMessage(null);
        picture.setReviewerId(null);
        picture.setReviewTime(null);
    }

    private void fillReviewPassParams(Picture picture, User reviewer, String reviewMessage) {
        fillReviewResultParams(picture, reviewer, PictureReviewStatusEnum.PASS.getValue(), reviewMessage);
    }

    private void fillReviewResultParams(Picture picture, User reviewer, Integer reviewStatus, String reviewMessage) {
        picture.setReviewStatus(reviewStatus);
        picture.setReviewMessage(reviewMessage);
        picture.setReviewerId(reviewer.getId());
        picture.setReviewTime(new Date());
    }

    private void clearReviewDetail(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        this.lambdaUpdate()
                .eq(Picture::getId, pictureId)
                .set(Picture::getReviewMessage, null)
                .set(Picture::getReviewerId, null)
                .set(Picture::getReviewTime, null)
                .update();
    }

    private void clearSourceUrl(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        this.lambdaUpdate()
                .eq(Picture::getId, pictureId)
                .set(Picture::getSourceUrl, null)
                .update();
    }
}


