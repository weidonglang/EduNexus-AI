package weidonglang.tianshiwebside.common.api;

import java.time.Instant;

/**
 * 全系统统一接口返回格式。
 *
 * 前端所有 Axios 请求都默认按这个结构读取数据：code 表示业务状态，message 表示提示信息，
 * data 承载真正的业务结果，timestamp 记录返回时间。这样学生端、教师端、管理端不用分别处理
 * 不同格式的返回值，代码结构更统一，也方便答辩时说明前后端分离的数据契约。
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, null, Instant.now());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, Instant.now());
    }
}
