package com.red.cloud.client.dto;

import java.time.Instant;

public record ClientOrderView(
    long id,
    String orderNo,
    String projectName,
    String inviteCode,
    int amountCents,
    String status,
    Instant createdTime
) {
}
