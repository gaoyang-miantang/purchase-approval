package com.acme.purchase.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把内部异常翻译成稳定的 HTTP 状态码 + 业务码 + 人话提示。
 * 前端因此可以统一处理，而不用每个接口单独判断。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResult.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest()
                .body(ApiResult.fail("VALIDATION_FAILED", message.isBlank() ? "参数校验失败" : message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiResult.fail("BAD_REQUEST", "请求参数格式不正确"));
    }

    /**
     * 乐观锁冲突：多人同时审批同一张单时触发。
     * 这个分支必须存在，否则前端会看到 500，用户以为系统坏了。
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiResult<Void>> handleConcurrent(Exception e) {
        log.warn("并发冲突: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.fail("CONCURRENT_MODIFICATION", "该申请单正在被其他人处理，请刷新后重试"));
    }

    /**
     * 静态资源缺失（favicon.ico、旧版本 assets 等）。
     *
     * <p>这不是服务端故障：浏览器自动请求 favicon 是常态，客户端拿着过期 URL 请求旧 chunk 也很常见。
     * 必须返回 404 且不打堆栈，否则每打开一次页面日志就刷一片红色 ERROR，
     * 真正的故障反而被淹没。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResource(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.fail("NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail("INTERNAL_ERROR", "服务器开小差了，请稍后再试"));
    }
}
