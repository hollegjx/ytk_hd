package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通过 URL 导入图片请求
 */
@Data
public class PictureUploadByUrlRequest implements Serializable {

    /**
     * 图片 URL
     */
    private String fileUrl;

    /**
     * 图片 ID（用于替换，新增时为空）
     */
    private Long id;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
