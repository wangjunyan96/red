package com.red.cloud.token;

/**
 * Token 类型。导入数据号时必选，结义等任务按此从库里取号。
 */
public record TokenTypeDef(
    String code,
    String name
) {
}
