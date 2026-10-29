package com.acme.purchase.common;

/**
 * 业务异常。带 HTTP 状态码，由 {@link GlobalExceptionHandler} 统一转换为响应体。
 *
 * <p>这里刻意区分 400/401/403/404/409 五类状态码：
 * 前端可以据此判断"是参数问题、没登录、没权限，还是数据被别人改过"，
 * 而不是所有错误都返回 200 + success=false。
 */
public class BizException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BizException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static BizException badRequest(String message) {
        return new BizException("BAD_REQUEST", message, 400);
    }

    public static BizException unauthorized(String message) {
        return new BizException("UNAUTHORIZED", message, 401);
    }

    public static BizException forbidden(String message) {
        return new BizException("FORBIDDEN", message, 403);
    }

    public static BizException notFound(String message) {
        return new BizException("NOT_FOUND", message, 404);
    }

    public static BizException conflict(String message) {
        return new BizException("CONFLICT", message, 409);
    }
}
