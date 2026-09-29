package com.red.cloud.client.dto;

public record ClientKeywordView(
    long id,
    String word,
    long projectId,
    String projectName
) {
}
