package com.red.cloud.task.dto;

import java.util.Map;

/**
 * 云手机脚本领取到的任务内容。
 * account / reunionCode 仍放在顶层，兼容英雄杀结义脚本；
 * 其它任务类型从 payload 取字段。
 */
public record TaskClaim(
    long id,
    String gameCode,
    String taskType,
    String account,
    String reunionCode,
    Map<String, String> payload,
    String runId
) {
}
