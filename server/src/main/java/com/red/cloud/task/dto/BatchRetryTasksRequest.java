package com.red.cloud.task.dto;

import java.util.List;

/**
 * 批量重试任务。switchToken 为 true 时从 Token 库另取未关联号。
 */
public record BatchRetryTasksRequest(
    List<Long> ids,
    Boolean switchToken
) {
}
