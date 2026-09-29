package com.red.cloud.client.dto;

public record ChatSendRequest(
    Long userId,
    String text,
    Long projectId
) {
}
