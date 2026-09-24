package com.aimovies.platform.web;

import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.User;
import com.aimovies.platform.repo.UserRepository;
import com.aimovies.platform.security.AuthSupport;
import com.aimovies.platform.security.PasswordUtil;
import com.aimovies.platform.security.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final TokenStore tokenStore;
    private final AuthSupport auth;

    public AuthController(UserRepository users, TokenStore tokenStore, AuthSupport auth) {
        this.users = users;
        this.tokenStore = tokenStore;
        this.auth = auth;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Dtos.RegisterRequest req) {
        if (req.username() == null || req.username().isBlank()
            || req.password() == null || req.password().isBlank()) {
            throw ApiException.badRequest("username and password are required");
        }
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("username already exists");
        }
        User u = new User();
        u.setUsername(req.username().trim());
        u.setPassword(PasswordUtil.hash(req.password()));
        u.setRole(Enums.Role.USER);
        u.setPoints(0);
        u = users.save(u);
        String token = tokenStore.issue(u.getId());
        return Map.of("token", token, "user", Views.user(u));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Dtos.LoginRequest req) {
        User u = users.findByUsername(req.username() == null ? "" : req.username().trim())
            .orElseThrow(() -> ApiException.unauthorized("invalid username or password"));
        if (!PasswordUtil.matches(req.password() == null ? "" : req.password(), u.getPassword())) {
            throw ApiException.unauthorized("invalid username or password");
        }
        String token = tokenStore.issue(u.getId());
        return Map.of("token", token, "user", Views.user(u));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        User u = auth.requireUser(request);
        return Views.user(u);
    }
}
