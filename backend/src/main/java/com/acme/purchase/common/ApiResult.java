package com.acme.purchase.common;

/**
 * 统一响应体。所有接口（含异常）都返回这个结构，前端只需处理一种格式。
 *
 * @param success 是否成功
 * @param code    业务码，成功为 OK，失败为具体错误码
 * @param message 提示信息
 * @param data    业务数据
 */
public record ApiResult<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "OK", "success", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(true, "OK", "success", null);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(false, code, message, null);
    }
}
