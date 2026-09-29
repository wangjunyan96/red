package com.red.cloud.user;

import com.red.cloud.user.dto.UserView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 用户领域服务：注册、登录，以及按手机号查询。
 * 密码只以 BCrypt 哈希形式写入 t_user.password。
 */
@Service
public class UserService {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按手机号注册新用户。
     * 手机号已存在时抛出 IllegalStateException，由 Controller 转成 409。
     */
    public UserView register(String phone, String password) {
        // 明文密码不落库，先转成 BCrypt 哈希。
        String encoded = passwordEncoder.encode(password);
        try {
            jdbcTemplate.update(
                "INSERT INTO t_user (phone, password, points, valid) VALUES (?, ?, 0, 1)",
                phone, encoded
            );
        } catch (DuplicateKeyException ex) {
            // phone 字段有唯一约束，并发注册时也会走这里。
            throw new IllegalStateException("该手机号已注册");
        }
        return findActiveByPhone(phone);
    }

    /**
     * 校验手机号和密码。
     * 账号不存在、已失效（valid != 1）或密码错误时统一返回 null，避免泄露账号是否存在。
     */
    public UserView login(String phone, String password) {
        UserRow row = findRowByPhone(phone);
        if (row == null || row.valid != 1) {
            return null;
        }
        if (!passwordEncoder.matches(password, row.password)) {
            return null;
        }
        return row.toView();
    }

    /** 注册成功后回查有效用户，转成对外视图。 */
    private UserView findActiveByPhone(String phone) {
        UserRow row = findRowByPhone(phone);
        if (row == null || row.valid != 1) {
            throw new IllegalStateException("user not found");
        }
        return row.toView();
    }

    /** 按手机号查库；查不到返回 null，而不是抛给上层。 */
    private UserRow findRowByPhone(String phone) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT id, phone, password, points, created_time, valid FROM t_user WHERE phone = ?",
                userRowMapper(),
                phone
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /** 按主键查有效用户。 */
    public UserView requireById(long id) {
        try {
            UserRow row = jdbcTemplate.queryForObject(
                "SELECT id, phone, password, points, created_time, valid FROM t_user WHERE id = ? AND valid = 1",
                userRowMapper(),
                id
            );
            if (row == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            return row.toView();
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    /** 必须在事务内调用：锁行并返回当前积分（分）。 */
    public long lockPoints(long userId) {
        try {
            Long points = jdbcTemplate.queryForObject(
                "SELECT points FROM t_user WHERE id = ? AND valid = 1 FOR UPDATE",
                Long.class,
                userId
            );
            return points == null ? 0L : points;
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    public void addPoints(long userId, long cents) {
        int updated = jdbcTemplate.update(
            "UPDATE t_user SET points = points + ? WHERE id = ? AND valid = 1",
            cents, userId
        );
        if (updated != 1) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    public void deductPoints(long userId, long cents) {
        int updated = jdbcTemplate.update(
            "UPDATE t_user SET points = points - ? WHERE id = ? AND valid = 1 AND points >= ?",
            cents, userId, cents
        );
        if (updated != 1) {
            throw new IllegalArgumentException("余额不足，请先充值");
        }
    }

    private static RowMapper<UserRow> userRowMapper() {
        return (rs, rowNum) -> new UserRow(
            rs.getLong("id"),
            rs.getString("phone"),
            rs.getString("password"),
            rs.getLong("points"),
            toInstant(rs.getTimestamp("created_time")),
            rs.getInt("valid")
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    /**
     * 库内用户行，仅在服务层使用。
     * valid：1 有效，其它值视为停用。
     */
    private record UserRow(
        long id,
        String phone,
        String password,
        long points,
        Instant createdTime,
        int valid
    ) {
        /** 去掉密码字段后再返回给接口层。 */
        private UserView toView() {
            return new UserView(id, phone, points, createdTime);
        }
    }
}
