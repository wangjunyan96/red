package com.red.cloud.task;

/**
 * 任务状态：待领取 / 执行中 / 成功 / 失败。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}
