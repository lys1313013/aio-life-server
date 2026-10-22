package top.aiolife.core.query;

/** 所有分页请求在绑定后校验，数据库插件再次拒绝非法分页。 */
public interface PaginationRequest {
    int MAX_PAGE_SIZE = 200;

    Integer getPage();
    Integer getPageSize();

    default int maximumPageSize() { return MAX_PAGE_SIZE; }

    default void validatePagination() {
        if (getPage() == null || getPageSize() == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }
        validate(getPage(), getPageSize(), maximumPageSize());
    }

    static void validate(long page, long pageSize, long maximum) {
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > maximum) {
            throw new IllegalArgumentException("pageSize 必须在 1 到 " + maximum + " 之间");
        }
        if (page - 1 > Long.MAX_VALUE / pageSize) {
            throw new IllegalArgumentException("分页偏移量过大");
        }
    }
}
