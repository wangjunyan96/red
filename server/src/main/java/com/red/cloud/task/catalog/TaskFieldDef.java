package com.red.cloud.task.catalog;

/**
 * 某任务类型的一个录入字段。后台按此动态渲染表单。
 */
public record TaskFieldDef(
    String key,
    String label,
    String inputType,
    boolean required,
    String placeholder
) {
}
