package com.acme.purchase.security;

import com.acme.purchase.common.BizException;
import com.acme.purchase.domain.User;
import com.acme.purchase.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：解析 Authorization 头，把当前用户放进线程上下文。
 * 只负责"你是谁"；"你能干什么"由 service 层的权限规则判断。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;
    private final UserRepository userRepository;

    public AuthInterceptor(TokenStore tokenStore, UserRepository userRepository) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) {
            throw BizException.unauthorized("缺少访问令牌");
        }
        Long userId = tokenStore.resolve(token);
        if (userId == null) {
            throw BizException.unauthorized("登录已过期，请重新登录");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BizException.unauthorized("用户不存在"));
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
