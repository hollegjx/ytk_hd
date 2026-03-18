package learn.gjx.tuku1.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRequest;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import learn.gjx.tuku1.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Core operations for the {@code picture} table plus custom upload/processing helpers.
 */
public interface PictureService extends IService<Picture> {

    /**
     * Upload or update an image and persist its metadata.
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * Build a typed query wrapper based on request filters.
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * Convert a {@link Picture} entity to a VO with user information.
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * Batch conversion helper that reduces N+1 lookups.
     */
    List<PictureVO> getPictureVOList(List<Picture> pictureList, HttpServletRequest request);

    /**
     * Convert a {@link Page} of entities to VOs while preserving pagination info.
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * Validate picture fields before update/persistence.
     */
    void validPicture(Picture picture);

    /**
     * Run Tencent CI processing on an existing picture.
     */
    PictureProcessResult processPicture(PictureProcessRequest pictureProcessRequest, HttpServletRequest request);
}
