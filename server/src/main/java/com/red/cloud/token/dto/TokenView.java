package com.red.cloud.token.dto;

import java.time.Instant;

/**
 * 后台 Token 列表中的一行。
 */
public record TokenView(
    long id,
    String token,
    String tokenType,
    String tokenTypeName,
    String status,
    String reunionCode,
    Instant createdTime,
    Instant updatedTime
) {
}
