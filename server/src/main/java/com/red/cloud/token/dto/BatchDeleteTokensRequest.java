package com.red.cloud.token.dto;

import java.util.List;

/**
 * 批量删除请求。
 */
public record BatchDeleteTokensRequest(
    List<Long> ids
) {
}
