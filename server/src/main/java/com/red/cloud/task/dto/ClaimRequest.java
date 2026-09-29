package com.red.cloud.task.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 云手机领取任务请求。
 * gameCode / taskType 可空：空则默认英雄杀-结义，兼容旧脚本。
 * 新脚本应显式传入，避免领到其它游戏的任务。
 */
public record ClaimRequest(
    @NotBlank(message = "deviceId is required")
    String deviceId,

    String gameCode,

    String taskType
) {
}
