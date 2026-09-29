package com.red.cloud.task.catalog;

import java.util.List;

/**
 * 某游戏下的一种任务。code 需与云手机脚本领取时上报的 taskType 一致。
 * tokenType 非空时，新增任务从对应 Token 库自动分配数据号，表单不必填 token。
 */
public record TaskTypeDef(
    String code,
    String name,
    String description,
    List<TaskFieldDef> fields,
    String tokenType
) {
    public TaskTypeDef(String code, String name, String description, List<TaskFieldDef> fields) {
        this(code, name, description, fields, null);
    }

    public boolean allocateFromTokenPool() {
        return tokenType != null && !tokenType.isBlank();
    }
}
