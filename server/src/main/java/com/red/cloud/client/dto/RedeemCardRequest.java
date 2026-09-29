package com.red.cloud.client.dto;

public record RedeemCardRequest(
    Long userId,
    String cardKey
) {
}
