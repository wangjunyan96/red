package com.red.cloud.client.dto;

import java.time.Instant;
import java.util.Map;

public record ClientMessageView(
    long id,
    String role,
    String msgType,
    String content,
    Map<String, Object> extra,
    Instant createdTime
) {
}
