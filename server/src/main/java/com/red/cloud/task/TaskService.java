package com.red.cloud.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red.cloud.task.catalog.TaskTypeCatalog;
import com.red.cloud.task.dto.AdminTaskView;
import com.red.cloud.task.dto.TaskClaim;
import com.red.cloud.token.TokenService;
import com.red.cloud.token.TokenTypeCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 云手机任务服务。
 * 脚本按 gameCode + taskType 领取对应任务，执行过程用心跳续租，结束后 report。
 */
@Service
public class TaskService {
    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TaskTypeCatalog catalog;
    private final TokenTypeCatalog tokenTypeCatalog;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final long leaseSeconds;

    public TaskService(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        TaskTypeCatalog catalog,
        TokenTypeCatalog tokenTypeCatalog,
        TokenService tokenService,
        ObjectMapper objectMapper,
        @Value("${app.task.lease-seconds:300}") long leaseSeconds
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.catalog = catalog;
        this.tokenTypeCatalog = tokenTypeCatalog;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * 领取下一条指定类型的任务。
     * 优先 PENDING；lease 超时的 RUNNING 也会被回收再分配。
     * 队列为空时返回 null，脚本按约定继续轮询。
     */
    public synchronized TaskClaim claim(String deviceId, String gameCode, String taskType) {
        String game = catalog.resolveGameCode(gameCode);
        String type = catalog.resolveTaskType(game, taskType);
        long now = Instant.now().getEpochSecond();
        return transactionTemplate.execute(status -> {
            List<TaskClaim> rows = jdbcTemplate.query(
                "SELECT id, game_code, task_type, account, reunion_code, payload FROM t_task "
                    + "WHERE valid = 1 AND game_code = ? AND task_type = ? "
                    + "AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until < ?)) "
                    + "ORDER BY id LIMIT 1 FOR UPDATE",
                claimRowMapper(),
                game, type, now
            );
            if (rows.isEmpty()) {
                return null;
            }

            TaskClaim picked = rows.get(0);
            String runId = UUID.randomUUID().toString();
            long leaseUntil = now + leaseSeconds;
            jdbcTemplate.update(
                "UPDATE t_task SET status = 'RUNNING', assigned_device = ?, run_id = ?, "
                    + "lease_until = ?, attempts = attempts + 1 WHERE id = ?",
                deviceId, runId, leaseUntil, picked.id()
            );
            if (!isBlank(picked.account())) {
                markDataAccount(picked.account(), "LINKED", picked.reunionCode());
            }
            return new TaskClaim(
                picked.id(),
                picked.gameCode(),
                picked.taskType(),
                picked.account(),
                picked.reunionCode(),
                picked.payload(),
                runId
            );
        });
    }

    /** 心跳续租：仅当前设备 + runId 仍持有该 RUNNING 任务时成功。 */
    public synchronized boolean heartbeat(long taskId, String deviceId, String runId) {
        long leaseUntil = Instant.now().getEpochSecond() + leaseSeconds;
        int updated = jdbcTemplate.update(
            "UPDATE t_task SET lease_until = ? "
                + "WHERE id = ? AND valid = 1 AND status = 'RUNNING' AND assigned_device = ? AND run_id = ?",
            leaseUntil, taskId, deviceId, runId
        );
        return updated == 1;
    }

    /**
     * 上报结果。成功则 token 记为 BOUND，失败记为 ERROR。
     * 只有仍持有该运行实例的设备才能改状态。
     */
    public synchronized boolean report(long taskId, String deviceId, String runId, String status, String error) {
        String newStatus;
        String lastError;
        String accountStatus;
        if ("done".equalsIgnoreCase(status)) {
            newStatus = TaskStatus.DONE.name();
            lastError = null;
            accountStatus = "BOUND";
        } else if ("failed".equalsIgnoreCase(status)) {
            newStatus = TaskStatus.FAILED.name();
            lastError = error;
            accountStatus = "ERROR";
        } else {
            return false;
        }

        String token = findToken(taskId, deviceId, runId);
        int updated = jdbcTemplate.update(
            "UPDATE t_task SET status = ?, last_error = ?, lease_until = 0 "
                + "WHERE id = ? AND valid = 1 AND status = 'RUNNING' AND assigned_device = ? AND run_id = ?",
            newStatus, lastError, taskId, deviceId, runId
        );
        if (updated != 1) {
            return false;
        }
        if (!isBlank(token)) {
            markDataAccount(token, accountStatus, null);
            if (TaskStatus.DONE.name().equals(newStatus)) {
                bumpReunionBindCount(taskId);
            }
        }
        return true;
    }

    public synchronized Map<TaskStatus, Integer> stats() {
        Map<TaskStatus, Integer> map = new EnumMap<>(TaskStatus.class);
        jdbcTemplate.query(
            "SELECT status, COUNT(*) AS c FROM t_task WHERE valid = 1 GROUP BY status",
            rs -> {
                try {
                    map.put(TaskStatus.valueOf(rs.getString("status")), rs.getInt("c"));
                } catch (IllegalArgumentException ignore) {
                    // 忽略未知状态，避免统计接口因脏数据失败。
                }
            }
        );
        return map;
    }

    /**
     * 后台新增任务：按注册表校验 payload。
     * 配置了 tokenType 的任务从对应 Token 库锁未关联号。
     * 结义按重逢码条数建任务；天机等按任务条数建任务。
     */
    public synchronized List<TaskClaim> addTasks(String gameCode, String taskType, Map<String, String> rawPayload) {
        String game = catalog.requireGame(gameCode).code();
        var def = catalog.requireTaskType(game, taskType);
        String type = def.code();
        Map<String, String> payload = catalog.validatePayload(game, type, rawPayload);
        if (def.allocateFromTokenPool()) {
            List<String> codes = parseReunionCodes(payload.get("reunionCodes"));
            boolean needsReunionCodes = def.fields().stream().anyMatch(field -> "reunionCodes".equals(field.key()));
            if (needsReunionCodes && codes.isEmpty()) {
                throw new IllegalArgumentException("请输入重逢码");
            }
            int count = codes.isEmpty() ? parseTaskCount(payload.get("count")) : codes.size();
            List<TaskClaim> created = transactionTemplate.execute(
                status -> createPooledTasks(game, type, def.tokenType(), count, codes)
            );
            return created == null ? List.of() : created;
        }
        return List.of(insertManualTask(game, type, payload));
    }

    /** 种子导入等旧入口：默认英雄杀-结义，显式指定 token。 */
    public synchronized TaskClaim addTask(String account, String reunionCode) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("account", account);
        payload.put("reunionCode", reunionCode);
        return insertManualTask(TaskTypeCatalog.GAME_HERO_KILLER, TaskTypeCatalog.TYPE_REUNION, payload);
    }

    public synchronized List<AdminTaskView> listTasks() {
        return listTasks(null, null, null, null);
    }

    public synchronized List<AdminTaskView> listTasks(
        String gameCode,
        String taskType,
        String status,
        String reunionCode
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, game_code, task_type, account, reunion_code, payload, status, assigned_device, attempts, last_error "
                + "FROM t_task WHERE valid = 1"
        );
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(gameCode)) {
            sql.append(" AND game_code = ?");
            args.add(gameCode.trim());
        }
        if (StringUtils.hasText(taskType)) {
            sql.append(" AND task_type = ?");
            args.add(taskType.trim());
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (StringUtils.hasText(reunionCode)) {
            sql.append(" AND reunion_code = ?");
            args.add(reunionCode.trim());
        }
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), adminRowMapper(), args.toArray());
    }

    /**
     * 批量删除任务。未完成任务占用的 LINKED 数据号会放回未关联。
     */
    public synchronized int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Integer deleted = transactionTemplate.execute(status -> {
            String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
            Object[] args = ids.toArray();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT account, status FROM t_task WHERE valid = 1 AND id IN (" + placeholders + ")",
                args
            );
            for (Map<String, Object> row : rows) {
                String taskStatus = String.valueOf(row.get("status"));
                if (TaskStatus.DONE.name().equals(taskStatus)) {
                    continue;
                }
                Object account = row.get("account");
                if (account != null) {
                    tokenService.releaseIfLinked(String.valueOf(account));
                }
            }
            return jdbcTemplate.update(
                "DELETE FROM t_task WHERE id IN (" + placeholders + ")",
                args
            );
        });
        return deleted == null ? 0 : deleted;
    }

    /**
     * 批量重试：任务重新变为待领取。
     * switchToken 为 true 时，从对应 Token 库另取未关联号；原 LINKED 号放回未关联，ERROR/BOUND 保持原状。
     * 不切换时沿用原 Token，并把占用中的号改回 LINKED，方便再次领取。
     */
    public synchronized int retryByIds(List<Long> ids, boolean switchToken) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Integer retried = transactionTemplate.execute(status -> {
            String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
            Object[] args = ids.toArray();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, game_code, task_type, account, reunion_code, payload, status "
                    + "FROM t_task WHERE valid = 1 AND id IN (" + placeholders + ") FOR UPDATE",
                args
            );
            if (rows.isEmpty()) {
                return 0;
            }
            if (switchToken) {
                reassignTokens(rows);
            } else {
                reuseTokens(rows);
            }
            List<Long> foundIds = rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
            String foundPlaceholders = String.join(",", foundIds.stream().map(id -> "?").toList());
            return jdbcTemplate.update(
                "UPDATE t_task SET status = 'PENDING', assigned_device = NULL, run_id = NULL, "
                    + "lease_until = 0, last_error = NULL WHERE valid = 1 AND id IN (" + foundPlaceholders + ")",
                foundIds.toArray()
            );
        });
        return retried == null ? 0 : retried;
    }

    public long leaseSeconds() {
        return leaseSeconds;
    }

    /** 切换 Token：先锁新号，再处理旧号，避免把刚释放的号立刻再分给自己。 */
    private void reassignTokens(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String game = String.valueOf(row.get("game_code"));
            String type = String.valueOf(row.get("task_type"));
            var def = catalog.requireTaskType(game, type);
            if (!def.allocateFromTokenPool()) {
                reuseToken(row);
                continue;
            }
            byType.computeIfAbsent(def.tokenType(), key -> new ArrayList<>()).add(row);
        }
        Map<Long, TokenService.PickedToken> assigned = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byType.entrySet()) {
            List<Map<String, Object>> group = entry.getValue();
            List<TokenService.PickedToken> tokens = tokenService.lockUnlinked(entry.getKey(), group.size());
            if (tokens.size() < group.size()) {
                String typeName = tokenTypeCatalog.name(entry.getKey());
                throw new IllegalArgumentException(
                    "【" + typeName + "】可用 Token 不足：需要 " + group.size()
                        + " 个，当前未关联仅 " + tokens.size() + " 个，请先导入 Token"
                );
            }
            for (int i = 0; i < group.size(); i++) {
                assigned.put(((Number) group.get(i).get("id")).longValue(), tokens.get(i));
            }
        }
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            TokenService.PickedToken picked = assigned.get(id);
            if (picked == null) {
                continue;
            }
            String oldAccount = row.get("account") == null ? "" : String.valueOf(row.get("account"));
            String reunionCode = row.get("reunion_code") == null ? null : String.valueOf(row.get("reunion_code"));
            String taskStatus = String.valueOf(row.get("status"));
            if (!TaskStatus.DONE.name().equals(taskStatus) && StringUtils.hasText(oldAccount)) {
                tokenService.releaseIfLinked(oldAccount);
            }
            Map<String, String> payload = readPayload(
                row.get("payload") == null ? null : String.valueOf(row.get("payload")),
                picked.token(),
                reunionCode
            );
            payload.put("account", picked.token());
            jdbcTemplate.update(
                "UPDATE t_task SET account = ?, payload = ? WHERE id = ?",
                picked.token(), writePayload(payload), id
            );
            tokenService.markLinked(picked.id(), reunionCode);
        }
    }

    /** 不换号：失败号从 ERROR 改回 LINKED，便于再次领取。 */
    private void reuseTokens(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            reuseToken(row);
        }
    }

    private void reuseToken(Map<String, Object> row) {
        Object account = row.get("account");
        if (account == null || String.valueOf(account).isBlank()) {
            return;
        }
        String reunionCode = row.get("reunion_code") == null ? null : String.valueOf(row.get("reunion_code"));
        markDataAccount(String.valueOf(account), "LINKED", reunionCode);
    }

    private String findToken(long taskId, String deviceId, String runId) {
        List<String> tokens = jdbcTemplate.query(
            "SELECT account FROM t_task WHERE id = ? AND assigned_device = ? AND run_id = ?",
            (rs, rowNum) -> rs.getString("account"),
            taskId, deviceId, runId
        );
        return tokens.isEmpty() ? null : tokens.get(0);
    }

    private int parseTaskCount(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 1;
        }
        try {
            int count = Integer.parseInt(raw.trim());
            if (count < 1 || count > 500) {
                throw new IllegalArgumentException("任务条数需为 1~500");
            }
            return count;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("任务条数必须是正整数");
        }
    }

    private List<TaskClaim> createPooledTasks(
        String game,
        String type,
        String tokenType,
        int count,
        List<String> codes
    ) {
        List<TokenService.PickedToken> tokens = tokenService.lockUnlinked(tokenType, count);
        if (tokens.size() < count) {
            String typeName = tokenTypeCatalog.name(tokenType);
            throw new IllegalArgumentException(
                "【" + typeName + "】可用 Token 不足：需要 " + count
                    + " 个，当前未关联仅 " + tokens.size() + " 个，请先导入 Token"
            );
        }
        List<TaskClaim> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String reunionCode = (codes != null && i < codes.size()) ? codes.get(i) : null;
            TokenService.PickedToken picked = tokens.get(i);
            if (!isBlank(reunionCode)) {
                upsertReunionCode(reunionCode);
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("account", picked.token());
            if (!isBlank(reunionCode)) {
                payload.put("reunionCode", reunionCode);
            }
            created.add(insertRow(game, type, picked.token(), reunionCode, payload));
            tokenService.markLinked(picked.id(), reunionCode);
        }
        return created;
    }

    private TaskClaim insertManualTask(String game, String type, Map<String, String> payload) {
        String account = payload.getOrDefault("account", "");
        String reunionCode = firstReunionCode(payload);
        if (!isBlank(account)) {
            upsertDataAccount(account, game);
        }
        if (!isBlank(reunionCode)) {
            upsertReunionCode(reunionCode);
        }
        Map<String, String> stored = new LinkedHashMap<>(payload);
        if (!isBlank(account)) {
            stored.put("account", account);
        }
        if (!isBlank(reunionCode)) {
            stored.put("reunionCode", reunionCode);
        }
        return insertRow(game, type, account, reunionCode, stored);
    }

    private TaskClaim insertRow(
        String game,
        String type,
        String account,
        String reunionCode,
        Map<String, String> payload
    ) {
        String payloadJson = writePayload(payload);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO t_task (game_code, task_type, account, reunion_code, payload, status, lease_until, attempts, valid) "
                    + "VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 0, 1)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, game);
            ps.setString(2, type);
            ps.setString(3, account);
            ps.setString(4, reunionCode);
            ps.setString(5, payloadJson);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long taskId = key == null ? 0L : key.longValue();
        return new TaskClaim(taskId, game, type, account, reunionCode, payload, null);
    }

    private void upsertDataAccount(String token, String tokenType) {
        jdbcTemplate.update(
            "INSERT INTO t_data_account (token, token_type, status, valid) VALUES (?, ?, 'UNLINKED', 1) "
                + "ON DUPLICATE KEY UPDATE valid = 1",
            token, tokenType
        );
    }

    private void upsertReunionCode(String reunionCode) {
        jdbcTemplate.update(
            "INSERT INTO t_reunion_code (reunion_code, bind_count, valid) VALUES (?, 0, 1) "
                + "ON DUPLICATE KEY UPDATE valid = 1",
            reunionCode
        );
    }

    private void markDataAccount(String token, String status, String reunionCode) {
        if (reunionCode == null) {
            jdbcTemplate.update(
                "UPDATE t_data_account SET status = ? WHERE token = ? AND valid = 1",
                status, token
            );
            return;
        }
        jdbcTemplate.update(
            "UPDATE t_data_account SET status = ?, reunion_code = ? WHERE token = ? AND valid = 1",
            status, reunionCode, token
        );
    }

    private void bumpReunionBindCount(long taskId) {
        jdbcTemplate.update(
            "UPDATE t_reunion_code SET bind_count = bind_count + 1 "
                + "WHERE reunion_code = (SELECT reunion_code FROM t_task WHERE id = ?) AND valid = 1",
            taskId
        );
    }

    private RowMapper<TaskClaim> claimRowMapper() {
        return (rs, rowNum) -> {
            String account = nullToEmpty(rs.getString("account"));
            String reunionCode = rs.getString("reunion_code");
            Map<String, String> payload = readPayload(rs.getString("payload"), account, reunionCode);
            return new TaskClaim(
                rs.getLong("id"),
                rs.getString("game_code"),
                rs.getString("task_type"),
                account,
                reunionCode,
                payload,
                null
            );
        };
    }

    private RowMapper<AdminTaskView> adminRowMapper() {
        return (rs, rowNum) -> {
            String gameCode = nullToEmpty(rs.getString("game_code"));
            String taskType = nullToEmpty(rs.getString("task_type"));
            String account = nullToEmpty(rs.getString("account"));
            String reunionCode = nullToEmpty(rs.getString("reunion_code"));
            return new AdminTaskView(
                rs.getLong("id"),
                gameCode,
                catalog.gameName(gameCode),
                taskType,
                catalog.taskName(gameCode, taskType),
                account,
                reunionCode,
                readPayload(rs.getString("payload"), account, reunionCode),
                rs.getString("status"),
                nullToEmpty(rs.getString("assigned_device")),
                rs.getInt("attempts"),
                nullToEmpty(rs.getString("last_error"))
            );
        };
    }

    private String writePayload(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("任务参数序列化失败", ex);
        }
    }

    private Map<String, String> readPayload(String json, String account, String reunionCode) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!isBlank(json)) {
            try {
                Map<String, String> parsed = objectMapper.readValue(json, PAYLOAD_TYPE);
                if (parsed != null) {
                    parsed.forEach((key, value) -> map.put(key, value == null ? "" : value));
                }
            } catch (Exception ignore) {
                // 旧数据或脏 JSON 时退回列字段。
            }
        }
        if (!map.containsKey("account") && !isBlank(account)) {
            map.put("account", account);
        }
        if (!map.containsKey("reunionCode") && !isBlank(reunionCode)) {
            map.put("reunionCode", reunionCode);
        }
        return map;
    }

    private static String firstReunionCode(Map<String, String> payload) {
        List<String> codes = parseReunionCodes(payload.get("reunionCodes"));
        if (!codes.isEmpty()) {
            return codes.get(0);
        }
        return emptyToNull(payload.get("reunionCode"));
    }

    public static List<String> parseReunionCodes(String raw) {
        List<String> codes = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return codes;
        }
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (String part : trimmed.split("[,，;；\\s]+")) {
                if (!part.isEmpty()) {
                    codes.add(part);
                }
            }
        }
        return codes;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
