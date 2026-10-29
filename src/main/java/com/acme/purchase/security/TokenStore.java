package com.acme.purchase.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简令牌存储（内存版）。
 *
 * <p>生产环境应替换为 JWT（无状态）或 Redis 共享会话，
 * 否则重启即掉线、多实例无法共享。这里为了演示"可运行、零外部依赖"而简化。
 */
@Component
public class TokenStore {

    private static final Duration TTL = Duration.ofHours(8);

    private record Session(Long userId, Instant expireAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(userId, Instant.now().plus(TTL)));
        return token;
    }

    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expireAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.userId();
    }

    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
