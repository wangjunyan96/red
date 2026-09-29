package com.red.cloud.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体：手机号 + 明文密码。
 * 密码入库前会在服务层做 BCrypt 加密，不会原样落库。
 */
public record RegisterRequest(
    /** 11 位国内手机号。 */
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号")
    String phone,

    /** 明文密码，长度 6~64。 */
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需为 6~64 位")
    String password
) {
}
