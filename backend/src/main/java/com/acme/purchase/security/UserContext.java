package com.acme.purchase.security;

import com.acme.purchase.common.BizException;
import com.acme.purchase.domain.User;

/**
 * 当前登录用户的线程上下文。由 {@link AuthInterceptor} 写入，请求结束后清理。
 */
public final class UserContext {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static User require() {
        User user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
