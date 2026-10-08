package com.red.cloud.task.dto;

import java.util.Map;

/**
 * 后台任务列表中的一行。
 */
public record AdminTaskView(
    long id,
    String gameCode,
    String gameName,
    String taskType,
    String taskName,
    String account,
    String reunionCode,
    Map<String, String> payload,
    String status,
    String assignedDevice,
    int attempts,
    String lastError
) {
}
