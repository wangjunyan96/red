package com.red.cloud.client.dto;

public record ClientProjectView(
    long id,
    String code,
    String name,
    String category,
    int priceCents,
    String gameCode,
    String taskType,
    String status,
    String tutorial
) {
}
