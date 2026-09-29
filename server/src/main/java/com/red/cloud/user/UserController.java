package com.red.cloud.user;

import com.red.cloud.user.dto.LoginRequest;
import com.red.cloud.user.dto.RegisterRequest;
import com.red.cloud.user.dto.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户接口：注册、登录。
 * 路径前缀 /api/v1/users。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 注册成功返回 201；手机号已被占用返回 409。 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UserView user = userService.register(request.phone().trim(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(wrapUser(user));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    /** 登录成功返回用户信息；账号或密码错误统一返回 401。 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        UserView user = userService.login(request.phone().trim(), request.password());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "手机号或密码错误"));
        }
        return ResponseEntity.ok(wrapUser(user));
    }

    /** 统一包装成 { "user": ... }，方便客户端解析。 */
    private static Map<String, Object> wrapUser(UserView user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", user);
        return body;
    }
}
