package learn.gjx.tuku1.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import learn.gjx.tuku1.model.dto.picture.PictureEditRequest;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRequest;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.dto.picture.PictureReviewRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadByBatchRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadByUrlRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUpdateRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import learn.gjx.tuku1.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * picture 表相关的核心服务接口，包含上传、查询与图片处理能力。
 */
public interface PictureService extends IService<Picture> {

    /**
     * 上传或更新图片，并同步保存元数据。
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 通过 URL 导入或替换图片，并同步保存元数据。
     */
    PictureVO uploadPictureByUrl(PictureUploadByUrlRequest pictureUploadByUrlRequest, User loginUser);

    /**
     * 管理员通过搜索关键词批量导入图片。
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 根据查询条件构建 MyBatis-Plus 的 QueryWrapper。
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 将 {@link Picture} 实体包装成带用户信息的 VO。
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 批量包装实体，避免 N+1 查询。
     */
    List<PictureVO> getPictureVOList(List<Picture> pictureList, HttpServletRequest request);

    /**
     * 将分页实体转换成分页 VO，保留分页信息。
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 数据更新前的字段校验。
     */
    void validPicture(Picture picture);

    /**
     * 对已经存在的图片执行数据万象处理。
     */
    PictureProcessResult processPicture(PictureProcessRequest pictureProcessRequest, HttpServletRequest request);

    /**
     * 管理员审核图片。
     */
    boolean reviewPicture(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 用户编辑图片，普通用户编辑后重新进入待审核。
     */
    boolean editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 管理员更新图片，更新后自动通过。
     */
    boolean updatePicture(PictureUpdateRequest pictureUpdateRequest, User loginUser);
}
