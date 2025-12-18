package learn.gjx.tuku1.model.dto.file;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传结果
 */
@Data
public class PictureUploadResult implements Serializable {

    /**
     * 图片 URL
     */
    private String url;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 图片体积（字节）
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    private static final long serialVersionUID = 1L;
}
