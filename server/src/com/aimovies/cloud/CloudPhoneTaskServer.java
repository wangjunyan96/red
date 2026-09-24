package com.aimovies.cloud;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Java server for cloud-phone task distribution.
 *
 * Task state is persisted in an embedded H2 database (JDBC), so claims,
 * heartbeats, and reports survive restarts. The CSV file is used only to seed
 * the database on first run (when the tasks table is empty).
 */
public final class CloudPhoneTaskServer {
    private static final Pattern TASK_ACTION_PATH = Pattern.compile("^/api/v1/tasks/(\\d+)/(heartbeat|report)$");
    private static final long DEFAULT_LEASE_SECONDS = 300L;
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_TASK_FILE = "data/tasks.csv";
    private static final String DEFAULT_DB_URL = "jdbc:h2:file:./data/taskdb;AUTO_SERVER=TRUE";

    private CloudPhoneTaskServer() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        int port = parsePort(System.getenv("PORT"));
        String taskFile = envOrDefault(System.getenv("TASK_FILE"), DEFAULT_TASK_FILE);
        long leaseSeconds = parseLongOrDefault(System.getenv("LEASE_SECONDS"), DEFAULT_LEASE_SECONDS);
        String apiKey = System.getenv("API_KEY");
        String dbUrl = envOrDefault(System.getenv("DB_URL"), DEFAULT_DB_URL);
        String dbUser = envOrDefault(System.getenv("DB_USER"), "sa");
        String dbPassword = System.getenv("DB_PASSWORD") == null ? "" : System.getenv("DB_PASSWORD");

        Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        connection.setAutoCommit(true);
        TaskStore.initSchema(connection);
        int seeded = TaskStore.seedIfEmpty(connection, TaskFileLoader.load(taskFile));

        TaskService taskService = new TaskService(connection, leaseSeconds);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        server.setExecutor(executor);

        server.createContext("/api/v1/health", wrapWithAuth(apiKey, exchange -> {
            if (!isMethod(exchange, "GET")) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\",\"time\":\"" + escapeJson(Instant.now().toString()) + "\"}");
        }));

        server.createContext("/api/v1/tasks/claim", wrapWithAuth(apiKey, exchange -> {
            if (!isMethod(exchange, "POST")) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            Map<String, String> params = readRequestParams(exchange);
            String deviceId = emptyToNull(params.get("deviceId"));
            if (deviceId == null) {
                sendJson(exchange, 400, "{\"error\":\"deviceId is required\"}");
                return;
            }

            Task task;
            try {
                task = taskService.claim(deviceId);
            } catch (SQLException ex) {
                sendJson(exchange, 500, "{\"error\":\"database error\"}");
                return;
            }
            if (task == null) {
                sendJson(exchange, 200, "{\"task\":null}");
                return;
            }

            String body = "{"
                + "\"task\":{"
                + "\"id\":" + task.id + ","
                + "\"account\":\"" + escapeJson(task.account) + "\","
                + "\"reunionCode\":\"" + escapeJson(task.reunionCode) + "\","
                + "\"runId\":\"" + escapeJson(task.runId) + "\""
                + "}"
                + "}";
            sendJson(exchange, 200, body);
        }));

        server.createContext("/api/v1/tasks/stats", wrapWithAuth(apiKey, exchange -> {
            if (!isMethod(exchange, "GET")) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            Map<TaskStatus, Integer> stats;
            try {
                stats = taskService.stats();
            } catch (SQLException ex) {
                sendJson(exchange, 500, "{\"error\":\"database error\"}");
                return;
            }
            String body = "{"
                + "\"pending\":" + stats.getOrDefault(TaskStatus.PENDING, 0) + ","
                + "\"running\":" + stats.getOrDefault(TaskStatus.RUNNING, 0) + ","
                + "\"done\":" + stats.getOrDefault(TaskStatus.DONE, 0) + ","
                + "\"failed\":" + stats.getOrDefault(TaskStatus.FAILED, 0)
                + "}";
            sendJson(exchange, 200, body);
        }));

        server.createContext("/api/v1/tasks/", wrapWithAuth(apiKey, exchange -> {
            String path = exchange.getRequestURI().getPath();
            Matcher matcher = TASK_ACTION_PATH.matcher(path);
            if (!matcher.matches()) {
                sendJson(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            if (!isMethod(exchange, "POST")) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }

            long taskId = Long.parseLong(matcher.group(1));
            String action = matcher.group(2);
            Map<String, String> params = readRequestParams(exchange);
            String deviceId = emptyToNull(params.get("deviceId"));
            String runId = emptyToNull(params.get("runId"));

            if (deviceId == null || runId == null) {
                sendJson(exchange, 400, "{\"error\":\"deviceId and runId are required\"}");
                return;
            }

            try {
                if ("heartbeat".equals(action)) {
                    boolean ok = taskService.heartbeat(taskId, deviceId, runId);
                    sendJson(exchange, 200, "{\"ok\":" + ok + "}");
                    return;
                }

                String status = emptyToNull(params.get("status"));
                String error = emptyToNull(params.get("error"));
                if (status == null) {
                    sendJson(exchange, 400, "{\"error\":\"status is required\"}");
                    return;
                }
                boolean ok = taskService.report(taskId, deviceId, runId, status, error);
                if (!ok) {
                    sendJson(exchange, 400, "{\"ok\":false,\"error\":\"invalid task state or status\"}");
                    return;
                }
                sendJson(exchange, 200, "{\"ok\":true}");
            } catch (SQLException ex) {
                sendJson(exchange, 500, "{\"error\":\"database error\"}");
            }
        }));

        server.createContext("/api/v1/admin/tasks", wrapWithAuth(apiKey, exchange -> {
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, "{\"tasks\":" + taskService.listTasksJson() + "}");
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    Map<String, String> params = readRequestParams(exchange);
                    String account = emptyToNull(params.get("account"));
                    String reunionCode = emptyToNull(params.get("reunionCode"));
                    if (account == null || reunionCode == null) {
                        sendJson(exchange, 400, "{\"error\":\"account and reunionCode are required\"}");
                        return;
                    }
                    Task created = taskService.addTask(account, reunionCode);
                    String body = "{"
                        + "\"task\":{"
                        + "\"id\":" + created.id + ","
                        + "\"account\":\"" + escapeJson(created.account) + "\","
                        + "\"reunionCode\":\"" + escapeJson(created.reunionCode) + "\","
                        + "\"status\":\"PENDING\""
                        + "}"
                        + "}";
                    sendJson(exchange, 201, body);
                    return;
                }
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            } catch (SQLException ex) {
                sendJson(exchange, 500, "{\"error\":\"database error\"}");
            }
        }));

        server.createContext("/api/v1/admin/", wrapWithAuth(apiKey, exchange -> {
            if (!isMethod(exchange, "POST")) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }
            sendJson(exchange, 501, "{\"error\":\"this admin endpoint is not implemented\"}");
        }));

        server.createContext("/", exchange -> sendJson(exchange, 404, "{\"error\":\"not found\"}"));
        server.start();
        System.out.println("CloudPhoneTaskServer started");
        System.out.println("Port: " + port);
        System.out.println("Database: " + dbUrl);
        System.out.println("Seed file: " + taskFile);
        System.out.println("Seeded tasks: " + seeded);
        System.out.println("Lease seconds: " + leaseSeconds);
        if (apiKey != null && !apiKey.isEmpty()) {
            System.out.println("API key auth: enabled");
        } else {
            System.out.println("API key auth: disabled");
        }
    }

    private static HttpHandler wrapWithAuth(String apiKey, HttpHandler inner) {
        if (apiKey == null || apiKey.isEmpty()) {
            return inner;
        }
        return exchange -> {
            String provided = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (!apiKey.equals(provided)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            inner.handle(exchange);
        };
    }

    private static int parsePort(String value) {
        long parsed = parseLongOrDefault(value, DEFAULT_PORT);
        if (parsed < 1 || parsed > 65535) {
            return DEFAULT_PORT;
        }
        return (int) parsed;
    }

    private static long parseLongOrDefault(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String envOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isMethod(HttpExchange exchange, String expected) {
        return expected.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String method) throws IOException {
        exchange.getResponseHeaders().set("Allow", method.toUpperCase(Locale.ROOT));
        sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
    }

    private static Map<String, String> readRequestParams(HttpExchange exchange) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        addQueryPairs(params, exchange.getRequestURI().getRawQuery());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) {
            return params;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            addJsonPairs(params, body);
        } else {
            addQueryPairs(params, body);
        }
        return params;
    }

    private static void addQueryPairs(Map<String, String> target, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            if (!key.isEmpty()) {
                target.put(key, value);
            }
        }
    }

    private static void addJsonPairs(Map<String, String> target, String json) {
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"((?:\\\\.|[^\"])*)\"|null|true|false|-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String rawValue = matcher.group(2);
            String value;
            if ("null".equals(rawValue)) {
                value = "";
            } else if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
                value = unescapeJsonString(matcher.group(3));
            } else {
                value = rawValue;
            }
            target.put(key, value);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String unescapeJsonString(String value) {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private enum TaskStatus {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }

    /** Minimal view of a task returned to clients. */
    private static final class Task {
        private final long id;
        private final String account;
        private final String reunionCode;
        private final String runId;

        private Task(long id, String account, String reunionCode, String runId) {
            this.id = id;
            this.account = account;
            this.reunionCode = reunionCode;
            this.runId = runId;
        }
    }

    /** A row parsed from the seed CSV file. */
    private static final class SeedRow {
        private final String account;
        private final String reunionCode;

        private SeedRow(String account, String reunionCode) {
            this.account = account;
            this.reunionCode = reunionCode;
        }
    }

    /**
     * Database-backed task operations. Methods are synchronized so a single
     * server instance never double-claims a row; the conditional UPDATEs also
     * guard correctness under concurrent access.
     */
    private static final class TaskService {
        private final Connection connection;
        private final long leaseSeconds;

        private TaskService(Connection connection, long leaseSeconds) {
            this.connection = connection;
            this.leaseSeconds = leaseSeconds;
        }

        private synchronized Task claim(String deviceId) throws SQLException {
            long now = Instant.now().getEpochSecond();
            connection.setAutoCommit(false);
            try {
                long id;
                String account;
                String reunionCode;
                String selectSql = "SELECT id, account, reunion_code FROM tasks "
                    + "WHERE status = 'PENDING' OR (status = 'RUNNING' AND lease_until < ?) "
                    + "ORDER BY id LIMIT 1 FOR UPDATE";
                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    select.setLong(1, now);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            connection.commit();
                            return null;
                        }
                        id = rs.getLong("id");
                        account = rs.getString("account");
                        reunionCode = rs.getString("reunion_code");
                    }
                }

                String runId = UUID.randomUUID().toString();
                long leaseUntil = now + leaseSeconds;
                String updateSql = "UPDATE tasks SET status = 'RUNNING', assigned_device = ?, run_id = ?, "
                    + "lease_until = ?, attempts = attempts + 1 WHERE id = ?";
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setString(1, deviceId);
                    update.setString(2, runId);
                    update.setLong(3, leaseUntil);
                    update.setLong(4, id);
                    update.executeUpdate();
                }
                connection.commit();
                return new Task(id, account, reunionCode, runId);
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }

        private synchronized boolean heartbeat(long taskId, String deviceId, String runId) throws SQLException {
            long leaseUntil = Instant.now().getEpochSecond() + leaseSeconds;
            String sql = "UPDATE tasks SET lease_until = ? "
                + "WHERE id = ? AND status = 'RUNNING' AND assigned_device = ? AND run_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, leaseUntil);
                ps.setLong(2, taskId);
                ps.setString(3, deviceId);
                ps.setString(4, runId);
                return ps.executeUpdate() == 1;
            }
        }

        private synchronized boolean report(long taskId, String deviceId, String runId, String status, String error)
                throws SQLException {
            String newStatus;
            String lastError;
            if ("done".equalsIgnoreCase(status)) {
                newStatus = "DONE";
                lastError = null;
            } else if ("failed".equalsIgnoreCase(status)) {
                newStatus = "FAILED";
                lastError = error;
            } else {
                return false;
            }
            String sql = "UPDATE tasks SET status = ?, last_error = ?, lease_until = 0 "
                + "WHERE id = ? AND status = 'RUNNING' AND assigned_device = ? AND run_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, newStatus);
                ps.setString(2, lastError);
                ps.setLong(3, taskId);
                ps.setString(4, deviceId);
                ps.setString(5, runId);
                return ps.executeUpdate() == 1;
            }
        }

        private synchronized Map<TaskStatus, Integer> stats() throws SQLException {
            Map<TaskStatus, Integer> map = new EnumMap<>(TaskStatus.class);
            String sql = "SELECT status, COUNT(*) AS c FROM tasks GROUP BY status";
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt("c");
                    try {
                        map.put(TaskStatus.valueOf(status), count);
                    } catch (IllegalArgumentException ignore) {
                        // Unknown status values are ignored in the summary.
                    }
                }
            }
            return map;
        }

        private synchronized Task addTask(String account, String reunionCode) throws SQLException {
            long nextId;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 AS next FROM tasks");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                nextId = rs.getLong("next");
            }
            String sql = "INSERT INTO tasks (id, account, reunion_code, status, lease_until, attempts) "
                + "VALUES (?, ?, ?, 'PENDING', 0, 0)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, nextId);
                ps.setString(2, account);
                ps.setString(3, reunionCode);
                ps.executeUpdate();
            }
            return new Task(nextId, account, reunionCode, null);
        }

        private synchronized String listTasksJson() throws SQLException {
            StringBuilder sb = new StringBuilder("[");
            String sql = "SELECT id, account, reunion_code, status, assigned_device, attempts, last_error "
                + "FROM tasks ORDER BY id";
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;
                    sb.append("{")
                        .append("\"id\":").append(rs.getLong("id")).append(",")
                        .append("\"account\":\"").append(escapeJson(rs.getString("account"))).append("\",")
                        .append("\"reunionCode\":\"").append(escapeJson(rs.getString("reunion_code"))).append("\",")
                        .append("\"status\":\"").append(escapeJson(rs.getString("status"))).append("\",")
                        .append("\"assignedDevice\":\"").append(escapeJson(rs.getString("assigned_device"))).append("\",")
                        .append("\"attempts\":").append(rs.getInt("attempts")).append(",")
                        .append("\"lastError\":\"").append(escapeJson(rs.getString("last_error"))).append("\"")
                        .append("}");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /** Schema creation and one-time CSV seeding. */
    private static final class TaskStore {
        private TaskStore() {
        }

        private static void initSchema(Connection connection) throws SQLException {
            String tasksDdl = "CREATE TABLE IF NOT EXISTS tasks ("
                + "id BIGINT PRIMARY KEY,"
                + "account VARCHAR(255) NOT NULL,"
                + "reunion_code VARCHAR(255) NOT NULL,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'PENDING',"
                + "assigned_device VARCHAR(255),"
                + "run_id VARCHAR(64),"
                + "lease_until BIGINT NOT NULL DEFAULT 0,"
                + "attempts INT NOT NULL DEFAULT 0,"
                + "last_error CLOB"
                + ")";

            // 数据号表: one QQ data account (login token) and its association state.
            // status values: UNLINKED(未关联), LINKED(已关联), BOUND(已绑定), ERROR(错误).
            String dataAccountsDdl = "CREATE TABLE IF NOT EXISTS data_accounts ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "token VARCHAR(1024) NOT NULL UNIQUE,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'UNLINKED',"
                + "reunion_code VARCHAR(255)"
                + ")";

            // 重逢码表: one reunion code and how many accounts are bound to it.
            String reunionCodesDdl = "CREATE TABLE IF NOT EXISTS reunion_codes ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "reunion_code VARCHAR(255) NOT NULL UNIQUE,"
                + "bind_count INT NOT NULL DEFAULT 0"
                + ")";

            try (Statement st = connection.createStatement()) {
                st.execute(tasksDdl);
                st.execute(dataAccountsDdl);
                st.execute(reunionCodesDdl);
            }
        }

        private static int seedIfEmpty(Connection connection, List<SeedRow> rows) throws SQLException {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tasks")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return 0;
                }
            }
            String sql = "INSERT INTO tasks (id, account, reunion_code, status, lease_until, attempts) "
                + "VALUES (?, ?, ?, 'PENDING', 0, 0)";
            int inserted = 0;
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                long id = 1;
                for (SeedRow row : rows) {
                    ps.setLong(1, id++);
                    ps.setString(2, row.account);
                    ps.setString(3, row.reunionCode);
                    ps.addBatch();
                    inserted++;
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
            return inserted;
        }
    }

    /** Parses the seed CSV file into rows (account, reunionCode). */
    private static final class TaskFileLoader {
        private TaskFileLoader() {
        }

        private static List<SeedRow> load(String filePath) throws IOException {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new IOException("Task file not found: " + path.toAbsolutePath());
            }

            List<SeedRow> rows = new ArrayList<>();
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.equalsIgnoreCase("account,reunionCode")) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                if (parts.length < 2) {
                    continue;
                }
                String account = parts[0].trim();
                String reunionCode = parts[1].trim();
                if (account.isEmpty() || reunionCode.isEmpty()) {
                    continue;
                }
                rows.add(new SeedRow(account, reunionCode));
            }
            return rows;
        }
    }
}
