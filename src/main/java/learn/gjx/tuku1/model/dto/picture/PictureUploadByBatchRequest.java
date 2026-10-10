package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量导入图片请求
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {

    /**
     * 搜索关键词
     */
    private String searchText;

    /**
     * 导入数量
     */
    private Integer count = 10;

    /**
     * 偏移量，跳过前多少张候选图片
     */
    private Integer offset = 0;

    /**
     * 图片名称前缀，未填写时使用搜索关键词
     */
    private String namePrefix;

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
