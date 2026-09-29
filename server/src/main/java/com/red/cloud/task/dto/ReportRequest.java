package com.red.cloud.task.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 任务结果上报。status 只接受 done / failed。
 */
public record ReportRequest(
    @NotBlank(message = "deviceId is required")
    String deviceId,

    @NotBlank(message = "runId is required")
    String runId,

    @NotBlank(message = "status is required")
    String status,

    String error
) {
}
