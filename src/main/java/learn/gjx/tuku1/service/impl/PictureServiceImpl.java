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
import learn.gjx.tuku1.model.dto.picture.PictureProcessRequest;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRule;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Concrete implementation of {@link PictureService} backed by MyBatis-Plus.
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. Validate request parameters
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "File cannot be empty");
        }

        // 2. Decide whether this is a create or update
        Picture picture = new Picture();
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        if (pictureId != null) {
            // If updating, ensure the picture exists
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            if (!exists) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Picture does not exist");
            }
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 3. Upload the file to COS
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        PictureUploadResult uploadResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        // 4. Populate picture metadata returned by CI
        picture.setUrl(uploadResult.getUrl());
        picture.setPicSize(uploadResult.getPicSize());
        picture.setPicWidth(uploadResult.getPicWidth());
        picture.setPicHeight(uploadResult.getPicHeight());
        picture.setPicScale(uploadResult.getPicScale());
        picture.setPicFormat(uploadResult.getPicFormat());
        picture.setUserId(loginUser.getId());

        // 5. Merge user-provided fields, falling back to defaults
        String picName = uploadResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getName())) {
            picName = pictureUploadRequest.getName();
        }
        picture.setName(picName);

        if (pictureUploadRequest != null) {
            picture.setIntroduction(pictureUploadRequest.getIntroduction());
            picture.setCategory(pictureUploadRequest.getCategory());
            // tags: List<String> -> JSON string
            if (CollUtil.isNotEmpty(pictureUploadRequest.getTags())) {
                picture.setTags(JSONUtil.toJsonStr(pictureUploadRequest.getTags()));
            }
        }

        // 6. Persist to the database
        boolean result = this.saveOrUpdate(picture);
        if (!result) {
            log.error("Failed to save picture, pictureId: {}", pictureId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Failed to save picture");
        }

        return PictureVO.objToVo(this.getById(picture.getId()));
    }

    @Override
    public void validPicture(Picture picture) {
        if (picture == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture info cannot be empty");
        }

        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();

        // An id must be present when updating
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid picture ID");
        }
        // URL must be present and within length limits
        if (StrUtil.isNotBlank(url) && url.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture URL is too long");
        }
        // Limit introduction length
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
        // Extract filters from the request DTO
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
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();

        // Build query conditions
        // Keyword search (matches name or introduction)
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }
        // Exact match conditions
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(picWidth != null, "picWidth", picWidth);
        queryWrapper.eq(picHeight != null, "picHeight", picHeight);
        queryWrapper.eq(picSize != null, "picSize", picSize);
        queryWrapper.eq(picScale != null, "picScale", picScale);
        queryWrapper.eq(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        // Fuzzy match conditions
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        // JSON array match on tags
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // Sorting
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), "asc".equals(sortOrder), sortField);

        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // Convert to VO
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // Attach user information
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
        // Entity list -> VO list
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 1) Fetch related users
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(userIdSet)) {
            return pictureVOList;
        }

        // Group by user id
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2) Fill user info into each VO
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

        // Bulk conversion
        List<PictureVO> pictureVOList = getPictureVOList(pictureList, request);
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;
    }

    @Override
    public PictureProcessResult processPicture(PictureProcessRequest pictureProcessRequest, HttpServletRequest request) {
        if (pictureProcessRequest == null || pictureProcessRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture ID cannot be empty");
        }
        Picture picture = this.getById(pictureProcessRequest.getPictureId());
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Picture does not exist");
        }
        List<PictureProcessRule> rules = pictureProcessRequest.getRules();
        if (CollUtil.isEmpty(rules)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Please provide at least one processing rule");
        }
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!isAdmin && !Objects.equals(picture.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "No permission to process this picture");
        }
        String objectKey = fileManager.extractKeyFromUrl(picture.getUrl());
        if (StrUtil.isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Picture path is invalid");
        }
        return fileManager.processPicture(objectKey, rules, pictureProcessRequest.getOutputDir());
    }
}


