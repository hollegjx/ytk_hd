package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片查询请求
 */
@Data
public class PictureQueryRequest implements Serializable {

    /**
     * 图片 ID
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

    /**
     * 文件体积
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
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 搜索关键词（同时搜名称和简介）
     */
    private String searchText;

    /**
     * 创建用户 ID
     */
    private Long userId;

    /**
     * 上传状态：0-草稿；1-已完成
     */
    private Integer uploadStatus;

    /**
     * 审核状态：0-待审核；1-通过；2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 当前页号
     */
    private Integer current = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式 asc/desc
     */
    private String sortOrder;

    private static final long serialVersionUID = 1L;
}
