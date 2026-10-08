package com.red.cloud.token.dto;

/**
 * 导入请求：Token 类型 + 整份 txt 文本（每行一个完整 token）。
 */
public record ImportTokensRequest(
    String tokenType,
    String content
) {
}
