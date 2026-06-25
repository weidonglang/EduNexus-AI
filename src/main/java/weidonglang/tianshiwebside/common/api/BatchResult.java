package weidonglang.tianshiwebside.common.api;

import java.util.List;

/**
 * 批量操作统一返回结构。
 *
 * 管理端批量审核、批量通知、导入导出等场景可能出现部分成功和部分失败，
 * 使用该结构可以让前端明确展示每条记录的处理结果。
 */
public record BatchResult<T>(
        int total,
        int successCount,
        int failureCount,
        List<Item<T>> items
) {
    public static <T> BatchResult<T> of(List<Item<T>> items) {
        List<Item<T>> safeItems = items == null ? List.of() : items;
        int successCount = (int) safeItems.stream().filter(Item::success).count();
        return new BatchResult<>(
                safeItems.size(),
                successCount,
                safeItems.size() - successCount,
                safeItems
        );
    }

    public record Item<T>(
            T id,
            boolean success,
            String code,
            String message
    ) {
        public static <T> Item<T> succeeded(T id) {
            return new Item<>(id, true, "0", "success");
        }

        public static <T> Item<T> failure(T id, String code, String message) {
            return new Item<>(id, false, code, message);
        }
    }
}
