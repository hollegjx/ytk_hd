package learn.gjx.tuku1.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.exception.ThrowUtils;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import learn.gjx.tuku1.model.vo.PictureTagCategory;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    /**
     * 获取标签和分类列表（用于前端展示）
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        // 标签列表
        List<String> tagList = Arrays.asList(
                "热门", "搞笑", "生活", "高清", "艺术",
                "校园", "背景", "简历", "创意"
        );
        // 分类列表
        List<String> categoryList = Arrays.asList(
                "模板", "电商", "表情包", "素材", "海报"
        );
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return BaseResponse.success(pictureTagCategory);
    }

    /**
     * 上传图片（登录用户可用）
     */
    @PostMapping("/upload")
    @AuthCheck
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile file,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(file, pictureUploadRequest, loginUser);
        return BaseResponse.success(pictureVO);
    }

    /**
     * 通过 URL 导入图片（登录用户可用）
     */
    @PostMapping("/upload/url")
    @AuthCheck
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadByUrlRequest uploadByUrlRequest,
                                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPictureByUrl(uploadByUrlRequest, loginUser);
        return BaseResponse.success(pictureVO);
    }

    /**
     * 批量导入图片（仅管理员可用）
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest uploadByBatchRequest,
                                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Integer successCount = pictureService.uploadPictureByBatch(uploadByBatchRequest, loginUser);
        return BaseResponse.success(successCount);
    }

    /**
     * 删除图片（仅管理员可用）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> deletePicture(@RequestBody PictureDeleteRequest deleteRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(id, loginUser);
        return BaseResponse.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest updateRequest,
                                               HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = pictureService.updatePicture(updateRequest, loginUser);
        return BaseResponse.success(result);
    }

    /**
     * 根据 ID 获取图片（仅管理员可用，不脱敏）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        return BaseResponse.success(picture);
    }

    /**
     * 根据 ID 获取图片（用户可用，脱敏）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        boolean publiclyVisible = Objects.equals(picture.getUploadStatus(), PictureUploadStatusEnum.COMPLETED.getValue())
                && Objects.equals(picture.getReviewStatus(), PictureReviewStatusEnum.PASS.getValue());
        if (!publiclyVisible) {
            User loginUser = userService.getLoginUser(request);
            if (!Objects.equals(picture.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "图片未审核通过");
            }
        }
        return BaseResponse.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用，不脱敏）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest queryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(queryRequest));
        return BaseResponse.success(picturePage);
    }

    /**
     * 分页获取待审核图片列表（仅管理员可用）
     */
    @PostMapping("/list/page/review")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Page<PictureVO>> listPictureReviewByPage(@RequestBody PictureQueryRequest queryRequest,
                                                                 HttpServletRequest request) {
        if (queryRequest == null) {
            queryRequest = new PictureQueryRequest();
        }
        if (queryRequest.getReviewStatus() == null) {
            queryRequest.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
        queryRequest.setUploadStatus(PictureUploadStatusEnum.COMPLETED.getValue());
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(queryRequest));
        return BaseResponse.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（用户可用，脱敏）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest queryRequest,
                                                             HttpServletRequest request) {
        return BaseResponse.success(pictureService.listPublicPicturePage(queryRequest, request));
    }

    /**
     * 分页获取我的图片列表（登录用户可用）
     */
    @PostMapping("/my/list/page/vo")
    @AuthCheck
    public BaseResponse<Page<PictureVO>> listMyPictureVOByPage(@RequestBody PictureQueryRequest queryRequest,
                                                               HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (queryRequest == null) {
            queryRequest = new PictureQueryRequest();
        }
        queryRequest.setUserId(loginUser.getId());
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页数量不能超过 20");
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(queryRequest));
        return BaseResponse.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 审核图片（仅管理员可用）
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> reviewPicture(@RequestBody PictureReviewRequest reviewRequest,
                                               HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = pictureService.reviewPicture(reviewRequest, loginUser);
        return BaseResponse.success(result);
    }

    /**
     * 鍥剧墖澶勭悊
     */
    @PostMapping("/process")
    public BaseResponse<PictureProcessResult> processPicture(@RequestBody PictureProcessRequest processRequest,
                                                             HttpServletRequest request) {
        PictureProcessResult result = pictureService.processPicture(processRequest, request);
        return BaseResponse.success(result);
    }

    /**
     * 编辑图片（用户可用，仅本人可编辑）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest editRequest,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = pictureService.editPicture(editRequest, loginUser);
        return BaseResponse.success(result);
    }
}
