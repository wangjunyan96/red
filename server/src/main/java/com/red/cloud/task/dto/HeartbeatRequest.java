package com.red.cloud.task.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 心跳续租请求。deviceId + runId 必须仍持有该任务。
 */
public record HeartbeatRequest(
    @NotBlank(message = "deviceId is required")
    String deviceId,

    @NotBlank(message = "runId is required")
    String runId
) {
}
