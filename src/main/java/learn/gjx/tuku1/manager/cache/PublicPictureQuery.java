package learn.gjx.tuku1.manager.cache;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import org.springframework.beans.BeanUtils;

import java.util.*;
import java.util.stream.Collectors;

/** Normalize once, then use the same request for both SQL and cache keys. */
public final class PublicPictureQuery {
    private static final Set<String> SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "name", "createTime", "editTime", "updateTime", "picSize",
            "picWidth", "picHeight", "picScale"));

    private PublicPictureQuery() {
    }

    public static PictureQueryRequest normalize(PictureQueryRequest source) {
        PictureQueryRequest query = new PictureQueryRequest();
        if (source != null) {
            BeanUtils.copyProperties(source, query);
        }
        if (query.getCurrent() == null) {
            query.setCurrent(1);
        }
        if (query.getPageSize() == null) {
            query.setPageSize(10);
        }
        require(query.getCurrent() > 0, "页码必须大于 0");
        require(query.getPageSize() > 0 && query.getPageSize() <= 20, "每页数量必须在 1 到 20 之间");
        query.setName(text(query.getName(), 128));
        query.setIntroduction(text(query.getIntroduction(), 500));
        query.setCategory(text(query.getCategory(), 64));
        query.setSearchText(text(query.getSearchText(), 128));
        query.setPicFormat(text(query.getPicFormat(), 32));
        if (query.getTags() != null) {
            require(query.getTags().size() <= 20, "标签数量不能超过 20");
            List<String> tags = query.getTags().stream().map(tag -> text(tag, 64))
                    .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
            query.setTags(tags.isEmpty() ? null : tags);
        }
        String sortField = text(query.getSortField(), 32);
        String sortOrder = text(query.getSortOrder(), 8);
        query.setSortField(sortField == null ? "createTime" : sortField);
        query.setSortOrder(sortOrder == null ? "desc" : sortOrder.toLowerCase(Locale.ROOT));
        require(SORT_FIELDS.contains(query.getSortField()), "不支持的排序字段");
        require(Arrays.asList("asc", "desc").contains(query.getSortOrder()), "排序方式必须是 asc 或 desc");
        require(query.getPicScale() == null || Double.isFinite(query.getPicScale()), "图片比例不合法");
        // Never trust client-supplied visibility, even for logged-in administrators.
        query.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        query.setUploadStatus(PictureUploadStatusEnum.COMPLETED.getValue());
        return query;
    }

    private static String text(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        require(value.length() <= maxLength, "查询条件过长");
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }
}
