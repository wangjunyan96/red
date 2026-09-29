package com.red.cloud.task.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 后台新增一条待执行任务。
 * gameCode + taskType 决定任务种类，payload 按该类型的字段填写。
 * 英雄杀-结义时 payload 只含 reunionCodes（可多行），Token 从库中自动分配。
 */
public record AddTaskRequest(
    @NotBlank(message = "请选择游戏")
    String gameCode,

    @NotBlank(message = "请选择任务类型")
    String taskType,

    Map<String, String> payload
) {
}
