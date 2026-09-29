package com.red.cloud.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体：手机号 + 明文密码。
 */
public record LoginRequest(
    @NotBlank(message = "请输入手机号")
    String phone,

    @NotBlank(message = "请输入密码")
    String password
) {
}
