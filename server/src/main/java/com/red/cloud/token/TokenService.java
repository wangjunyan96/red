package com.red.cloud.token;

import com.red.cloud.token.dto.TokenView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据号 Token 管理：按类型导入、按状态/类型/重逢码查询、批量删除、给任务锁号。
 */
@Service
public class TokenService {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TokenTypeCatalog tokenTypeCatalog;

    public TokenService(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        TokenTypeCatalog tokenTypeCatalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.tokenTypeCatalog = tokenTypeCatalog;
    }

    /**
     * 解析 txt：每行一个完整 token，空行和 # 开头忽略。
     * 已存在的 token 计为 skipped，不覆盖原状态和类型。
     */
    public Map<String, Integer> importTokens(String tokenType, String content) {
        String type = tokenTypeCatalog.require(tokenType).code();
        List<String> tokens = parseTokenLines(content);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("文件没有有效 token");
        }
        int inserted = 0;
        final int batchSize = 100;
        for (int from = 0; from < tokens.size(); from += batchSize) {
            List<String> chunk = new ArrayList<>(tokens.subList(from, Math.min(from + batchSize, tokens.size())));
            inserted += insertChunk(chunk, type);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", tokens.size());
        result.put("inserted", inserted);
        result.put("skipped", Math.max(0, tokens.size() - inserted));
        return result;
    }

    private int insertChunk(List<String> chunk, String type) {
        Integer inserted = transactionTemplate.execute(status -> {
            int before = countAccounts();
            StringBuilder sql = new StringBuilder(
                "INSERT INTO t_data_account (token, token_type, status, valid) VALUES "
            );
            Object[] args = new Object[chunk.size() * 2];
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append("(?, ?, 'UNLINKED', 1)");
                args[i * 2] = chunk.get(i);
                args[i * 2 + 1] = type;
            }
            sql.append(" ON DUPLICATE KEY UPDATE token = token");
            jdbcTemplate.update(sql.toString(), args);
            return Math.max(0, countAccounts() - before);
        });
        return inserted == null ? 0 : inserted;
    }

    private int countAccounts() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_data_account WHERE valid = 1",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public List<TokenView> list(String status, String tokenType, String reunionCode) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, token, token_type, status, reunion_code, created_time, updated_time "
                + "FROM t_data_account WHERE valid = 1"
        );
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(tokenType)) {
            sql.append(" AND token_type = ?");
            args.add(tokenTypeCatalog.require(tokenType).code());
        }
        if (StringUtils.hasText(reunionCode)) {
            sql.append(" AND reunion_code = ?");
            args.add(reunionCode.trim());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), tokenRowMapper(), args.toArray());
    }

    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        Object[] args = ids.toArray();
        return jdbcTemplate.update(
            "DELETE FROM t_data_account WHERE id IN (" + placeholders + ")",
            args
        );
    }

    /**
     * 锁定若干未关联 Token，供创建任务时分配。必须在调用方事务内执行。
     */
    public List<PickedToken> lockUnlinked(String tokenType, int count) {
        if (count <= 0) {
            return List.of();
        }
        String type = tokenTypeCatalog.require(tokenType).code();
        return jdbcTemplate.query(
            "SELECT id, token FROM t_data_account "
                + "WHERE valid = 1 AND status = 'UNLINKED' AND token_type = ? "
                + "ORDER BY id ASC LIMIT ? FOR UPDATE",
            (rs, rowNum) -> new PickedToken(rs.getLong("id"), rs.getString("token")),
            type, count
        );
    }

    public void markLinked(long id, String reunionCode) {
        jdbcTemplate.update(
            "UPDATE t_data_account SET status = 'LINKED', reunion_code = ? WHERE id = ? AND valid = 1",
            reunionCode, id
        );
    }

    /** 未完成任务被删除时，把仍占用的数据号放回未关联。已绑定/错误号不动。 */
    public void releaseIfLinked(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        jdbcTemplate.update(
            "UPDATE t_data_account SET status = 'UNLINKED', reunion_code = NULL "
                + "WHERE token = ? AND valid = 1 AND status = 'LINKED'",
            token
        );
    }

    public static List<String> parseTokenLines(String content) {
        List<String> tokens = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return tokens;
        }
        String[] lines = content.split("\\r\\n|\\n|\\r");
        for (String raw : lines) {
            String line = sanitizeTokenLine(raw);
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equalsIgnoreCase("token") || line.toLowerCase(Locale.ROOT).startsWith("token,")) {
                continue;
            }
            if (line.length() > 1024) {
                line = line.substring(0, 1024);
            }
            tokens.add(line);
        }
        return tokens;
    }

    private static String sanitizeTokenLine(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\uFEFF", "").replace("\u0000", "").trim();
    }

    private RowMapper<TokenView> tokenRowMapper() {
        return (rs, rowNum) -> {
            String type = emptyToNull(rs.getString("token_type"));
            return new TokenView(
                rs.getLong("id"),
                rs.getString("token"),
                type,
                tokenTypeCatalog.name(type),
                rs.getString("status"),
                emptyToNull(rs.getString("reunion_code")),
                toInstant(rs.getTimestamp("created_time")),
                toInstant(rs.getTimestamp("updated_time"))
            );
        };
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public record PickedToken(long id, String token) {
    }
}
