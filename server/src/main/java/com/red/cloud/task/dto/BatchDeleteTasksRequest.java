package com.red.cloud.task.dto;

import java.util.List;

/**
 * 批量删除任务。
 */
public record BatchDeleteTasksRequest(
    List<Long> ids
) {
}
