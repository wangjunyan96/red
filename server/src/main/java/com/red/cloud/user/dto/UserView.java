package com.red.cloud.user.dto;

import java.time.Instant;

/**
 * 返回给客户端的用户视图。
 * 故意不包含 password，避免密码哈希泄露到接口响应里。
 */
public record UserView(
    /** 用户主键。 */
    long id,
    /** 登录手机号。 */
    String phone,
    /** 当前积分。 */
    long points,
    /** 账号创建时间。 */
    Instant createdTime
) {
}
