package com.red.cloud.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red.cloud.client.dto.ClientKeywordView;
import com.red.cloud.client.dto.ClientMessageView;
import com.red.cloud.client.dto.ClientOrderView;
import com.red.cloud.client.dto.ClientProjectView;
import com.red.cloud.task.TaskService;
import com.red.cloud.task.dto.TaskClaim;
import com.red.cloud.user.UserService;
import com.red.cloud.user.dto.UserView;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C 端：项目、聊天下单、订单、卡密。
 */
@Service
public class ClientService {
    private static final Pattern BRACKET_CODE = Pattern.compile("\\[([A-Za-z0-9_-]{6,64})\\]");
    private static final Pattern PLAIN_CODE = Pattern.compile("^[A-Za-z0-9_-]{6,64}$");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserService userService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public ClientService(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        UserService userService,
        TaskService taskService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.userService = userService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    public List<ClientProjectView> listProjects() {
        return jdbcTemplate.query(
            "SELECT id, code, name, category, price_cents, game_code, task_type, status, tutorial "
                + "FROM t_c_project WHERE valid = 1 ORDER BY sort_no, id",
            projectRowMapper()
        );
    }

    public List<ClientKeywordView> listKeywords() {
        return jdbcTemplate.query(
            "SELECT k.id, k.word, k.project_id, p.name AS project_name "
                + "FROM t_c_keyword k JOIN t_c_project p ON p.id = k.project_id "
                + "WHERE k.valid = 1 AND p.valid = 1 ORDER BY k.id",
            (rs, rowNum) -> new ClientKeywordView(
                rs.getLong("id"),
                rs.getString("word"),
                rs.getLong("project_id"),
                rs.getString("project_name")
            )
        );
    }

    public List<ClientMessageView> listMessages(long userId) {
        userService.requireById(userId);
        ensureWelcome(userId);
        return jdbcTemplate.query(
            "SELECT id, role, msg_type, content, extra, created_time "
                + "FROM t_c_message WHERE user_id = ? ORDER BY id",
            messageRowMapper(),
            userId
        );
    }

    public List<ClientOrderView> listOrders(long userId) {
        userService.requireById(userId);
        return jdbcTemplate.query(
            "SELECT o.id, o.order_no, p.name AS project_name, o.invite_code, o.amount_cents, "
                + "CASE WHEN t.status = 'DONE' THEN 'DONE' WHEN t.status = 'FAILED' THEN 'FAILED' ELSE 'PROCESSING' END AS status, "
                + "o.created_time "
                + "FROM t_c_order o "
                + "JOIN t_c_project p ON p.id = o.project_id "
                + "LEFT JOIN t_task t ON t.id = o.task_id "
                + "WHERE o.user_id = ? AND o.valid = 1 ORDER BY o.id DESC",
            orderRowMapper(),
            userId
        );
    }

    public Map<String, Object> sendChat(long userId, String rawText, Long projectId) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("请输入内容");
        }
        userService.requireById(userId);
        insertMessage(userId, "user", "text", text, null);
        try {
            Map<String, Object> result = transactionTemplate.execute(status -> placeOrder(userId, text, projectId));
            return result == null ? Map.of() : result;
        } catch (IllegalArgumentException ex) {
            insertMessage(userId, "system", "text", ex.getMessage() == null ? "下单失败" : ex.getMessage(), null);
            throw ex;
        } catch (RuntimeException ex) {
            insertMessage(userId, "system", "text", "下单失败，请稍后重试", null);
            throw ex;
        }
    }

    public UserView redeemCard(long userId, String cardKey) {
        String key = cardKey == null ? "" : cardKey.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("请输入卡密");
        }
        return transactionTemplate.execute(status -> {
            userService.lockPoints(userId);
            List<Map<String, Object>> cards = jdbcTemplate.queryForList(
                "SELECT id, amount_cents, used FROM t_c_card WHERE card_key = ? AND valid = 1 FOR UPDATE",
                key
            );
            if (cards.isEmpty()) {
                throw new IllegalArgumentException("卡密无效");
            }
            Map<String, Object> card = cards.get(0);
            if (((Number) card.get("used")).intValue() == 1) {
                throw new IllegalArgumentException("卡密已使用");
            }
            int amount = ((Number) card.get("amount_cents")).intValue();
            jdbcTemplate.update(
                "UPDATE t_c_card SET used = 1, used_user_id = ?, used_time = CURRENT_TIMESTAMP WHERE id = ?",
                userId, card.get("id")
            );
            userService.addPoints(userId, amount);
            insertMessage(userId, "system", "text", "卡密兑换成功，到账 ¥" + formatYuan(amount), null);
            return userService.requireById(userId);
        });
    }

    private Map<String, Object> placeOrder(long userId, String text, Long projectId) {
        long balance = userService.lockPoints(userId);

        ClientProjectView project = resolveProject(text, projectId);
        if (project == null) {
            throw new IllegalArgumentException("请先在项目中心选择项目，或在消息里带上关键词（如：结义、重逢）。");
        }
        if (!"OPEN".equals(project.status())) {
            throw new IllegalArgumentException("「" + project.name() + "」暂未接单。");
        }

        List<String> codes = extractInviteCodes(text);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("没有识别到重逢码/口令。可直接发送重逢码，或使用 [口令] 格式。");
        }

        long total = (long) project.priceCents() * codes.size();
        if (balance < total) {
            throw new IllegalArgumentException(
                "余额不足：需要 ¥" + formatYuan(total) + "，当前 ¥" + formatYuan(balance) + "，请先充值。"
            );
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("reunionCodes", String.join("\n", codes));
        List<TaskClaim> tasks = taskService.addTasks(project.gameCode(), project.taskType(), payload);
        userService.deductPoints(userId, total);
        long newBalance = balance - total;

        List<Map<String, Object>> created = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            TaskClaim claim = tasks.get(i);
            String code = i < codes.size() ? codes.get(i) : claim.reunionCode();
            long orderId = insertOrder(userId, project.id(), claim.id(), code, project.priceCents());
            String orderNo = "ZN" + orderId;
            jdbcTemplate.update("UPDATE t_c_order SET order_no = ? WHERE id = ?", orderNo, orderId);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("orderNo", orderNo);
            extra.put("projectName", project.name());
            extra.put("inviteCode", code);
            extra.put("amountCents", project.priceCents());
            extra.put("balanceCents", newBalance);
            extra.put("createdTime", Instant.now().toString());
            insertMessage(userId, "system", "order", "下单成功", extra);
            created.add(extra);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userService.requireById(userId));
        result.put("orders", created);
        return result;
    }

    private ClientProjectView resolveProject(String text, Long projectId) {
        if (projectId != null && projectId > 0) {
            return findProject(projectId);
        }
        List<ClientKeywordView> keywords = listKeywords();
        keywords.sort((a, b) -> Integer.compare(b.word().length(), a.word().length()));
        for (ClientKeywordView keyword : keywords) {
            if (text.contains(keyword.word())) {
                return findProject(keyword.projectId());
            }
        }
        List<ClientProjectView> open = listProjects().stream().filter(item -> "OPEN".equals(item.status())).toList();
        if (open.size() == 1) {
            return open.get(0);
        }
        return null;
    }

    private ClientProjectView findProject(long id) {
        List<ClientProjectView> rows = jdbcTemplate.query(
            "SELECT id, code, name, category, price_cents, game_code, task_type, status, tutorial "
                + "FROM t_c_project WHERE id = ? AND valid = 1",
            projectRowMapper(),
            id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> extractInviteCodes(String text) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        Matcher matcher = BRACKET_CODE.matcher(text);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        for (String part : TaskService.parseReunionCodes(text)) {
            if (PLAIN_CODE.matcher(part).matches()) {
                codes.add(part);
            }
        }
        return new ArrayList<>(codes);
    }

    private void ensureWelcome(long userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_c_message WHERE user_id = ?",
            Integer.class,
            userId
        );
        if (count != null && count > 0) {
            return;
        }
        insertMessage(
            userId,
            "system",
            "text",
            "你好，这里是网页下单。发送重逢码或带 [口令] 的内容，即可为「英雄杀-结义」下单。也可以先去项目中心点选项目。",
            null
        );
    }

    private long insertOrder(long userId, long projectId, long taskId, String inviteCode, int amountCents) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO t_c_order (order_no, user_id, project_id, task_id, invite_code, amount_cents, status, valid) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING', 1)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, java.util.UUID.randomUUID().toString().replace("-", ""));
            ps.setLong(2, userId);
            ps.setLong(3, projectId);
            ps.setLong(4, taskId);
            ps.setString(5, inviteCode);
            ps.setInt(6, amountCents);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private void insertMessage(long userId, String role, String msgType, String content, Map<String, Object> extra) {
        String extraJson = extra == null ? null : writeJson(extra);
        jdbcTemplate.update(
            "INSERT INTO t_c_message (user_id, role, msg_type, content, extra) VALUES (?, ?, ?, ?, ?)",
            userId, role, msgType, content, extraJson
        );
    }

    private String writeJson(Map<String, Object> extra) {
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String formatYuan(long cents) {
        return String.format("%.2f", cents / 100.0);
    }

    private static RowMapper<ClientProjectView> projectRowMapper() {
        return (rs, rowNum) -> new ClientProjectView(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getInt("price_cents"),
            rs.getString("game_code"),
            rs.getString("task_type"),
            rs.getString("status"),
            rs.getString("tutorial")
        );
    }

    private RowMapper<ClientMessageView> messageRowMapper() {
        return (rs, rowNum) -> new ClientMessageView(
            rs.getLong("id"),
            rs.getString("role"),
            rs.getString("msg_type"),
            rs.getString("content"),
            readExtra(rs.getString("extra")),
            toInstant(rs.getTimestamp("created_time"))
        );
    }

    private static RowMapper<ClientOrderView> orderRowMapper() {
        return (rs, rowNum) -> new ClientOrderView(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getString("project_name"),
            rs.getString("invite_code"),
            rs.getInt("amount_cents"),
            rs.getString("status"),
            toInstant(rs.getTimestamp("created_time"))
        );
    }

    private Map<String, Object> readExtra(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
