package com.aimovies.platform.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory bearer-token store (userId per token). Tokens are ephemeral and
 * reset on restart; a persistent/JWT scheme can replace this later.
 */
@Component
public class TokenStore {
    private final Map<String, Long> tokenToUser = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUser.put(token, userId);
        return token;
    }

    public Long resolve(String token) {
        if (token == null) {
            return null;
        }
        return tokenToUser.get(token);
    }

    public void revoke(String token) {
        if (token != null) {
            tokenToUser.remove(token);
        }
    }
}
