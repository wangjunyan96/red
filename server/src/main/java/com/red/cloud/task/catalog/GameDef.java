package com.red.cloud.task.catalog;

import java.util.List;

/**
 * 一种游戏。code 需与云手机脚本领取时上报的 gameCode 一致。
 */
public record GameDef(
    String code,
    String name,
    List<TaskTypeDef> tasks
) {
}
