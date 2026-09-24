package com.aimovies.platform.security;

import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.User;
import com.aimovies.platform.repo.UserRepository;
import com.aimovies.platform.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Resolves the current user from the request and enforces roles. */
@Component
public class AuthSupport {
    private final TokenStore tokenStore;
    private final UserRepository users;

    public AuthSupport(TokenStore tokenStore, UserRepository users) {
        this.tokenStore = tokenStore;
        this.users = users;
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        String x = request.getHeader("X-Token");
        return x == null || x.isBlank() ? null : x.trim();
    }

    public User currentUser(HttpServletRequest request) {
        Long userId = tokenStore.resolve(extractToken(request));
        if (userId == null) {
            return null;
        }
        return users.findById(userId).orElse(null);
    }

    public User requireUser(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            throw ApiException.unauthorized("login required");
        }
        return user;
    }

    public User requireAdmin(HttpServletRequest request) {
        User user = requireUser(request);
        if (user.getRole() != Enums.Role.ADMIN) {
            throw ApiException.forbidden("admin only");
        }
        return user;
    }
}
