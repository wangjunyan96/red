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
 * No external dependencies are required; run with javac/java only.
 */
public final class CloudPhoneTaskServer {
    private static final Pattern TASK_ACTION_PATH = Pattern.compile("^/api/v1/tasks/(\\d+)/(heartbeat|report)$");
    private static final long DEFAULT_LEASE_SECONDS = 300L;
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_TASK_FILE = "data/tasks.csv";

    private CloudPhoneTaskServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(System.getenv("PORT"));
        String taskFile = envOrDefault(System.getenv("TASK_FILE"), DEFAULT_TASK_FILE);
        long leaseSeconds = parseLongOrDefault(System.getenv("LEASE_SECONDS"), DEFAULT_LEASE_SECONDS);
        String apiKey = System.getenv("API_KEY");

        List<Task> tasks = TaskFileLoader.load(taskFile);
        TaskService taskService = new TaskService(tasks, leaseSeconds);

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

            Task task = taskService.claim(deviceId);
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
            Map<TaskStatus, Integer> stats = taskService.stats();
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
        }));

        server.createContext("/api/v1/admin/", wrapWithAuth(apiKey, exchange -> {
            if (!isMethod(exchange, "POST")) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }
            sendJson(exchange, 501, "{\"error\":\"admin create/update endpoints are postponed; import tasks via data/tasks.csv for now\"}");
        }));

        server.createContext("/", exchange -> sendJson(exchange, 404, "{\"error\":\"not found\"}"));
        server.start();
        System.out.println("CloudPhoneTaskServer started");
        System.out.println("Port: " + port);
        System.out.println("Task file: " + taskFile);
        System.out.println("Loaded tasks: " + tasks.size());
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

    private static final class Task {
        private final long id;
        private final String account;
        private final String reunionCode;
        private TaskStatus status;
        private String assignedDevice;
        private String runId;
        private long leaseUntilEpochSecond;
        private int attempts;
        private String lastError;

        private Task(long id, String account, String reunionCode) {
            this.id = id;
            this.account = account;
            this.reunionCode = reunionCode;
            this.status = TaskStatus.PENDING;
        }
    }

    private static final class TaskService {
        private final List<Task> tasks;
        private final long leaseSeconds;

        private TaskService(List<Task> tasks, long leaseSeconds) {
            this.tasks = tasks;
            this.leaseSeconds = leaseSeconds;
        }

        private synchronized Task claim(String deviceId) {
            long now = Instant.now().getEpochSecond();
            for (Task task : tasks) {
                boolean pending = task.status == TaskStatus.PENDING;
                boolean expiredRunning = task.status == TaskStatus.RUNNING && task.leaseUntilEpochSecond < now;
                if (!pending && !expiredRunning) {
                    continue;
                }
                task.status = TaskStatus.RUNNING;
                task.assignedDevice = deviceId;
                task.runId = UUID.randomUUID().toString();
                task.leaseUntilEpochSecond = now + leaseSeconds;
                task.attempts += 1;
                return task;
            }
            return null;
        }

        private synchronized boolean heartbeat(long taskId, String deviceId, String runId) {
            Task task = find(taskId);
            if (task == null || task.status != TaskStatus.RUNNING) {
                return false;
            }
            if (!deviceId.equals(task.assignedDevice) || !runId.equals(task.runId)) {
                return false;
            }
            task.leaseUntilEpochSecond = Instant.now().getEpochSecond() + leaseSeconds;
            return true;
        }

        private synchronized boolean report(long taskId, String deviceId, String runId, String status, String error) {
            Task task = find(taskId);
            if (task == null || task.status != TaskStatus.RUNNING) {
                return false;
            }
            if (!deviceId.equals(task.assignedDevice) || !runId.equals(task.runId)) {
                return false;
            }
            if ("done".equalsIgnoreCase(status)) {
                task.status = TaskStatus.DONE;
                task.lastError = null;
            } else if ("failed".equalsIgnoreCase(status)) {
                task.status = TaskStatus.FAILED;
                task.lastError = error;
            } else {
                return false;
            }
            task.leaseUntilEpochSecond = 0;
            return true;
        }

        private synchronized Map<TaskStatus, Integer> stats() {
            Map<TaskStatus, Integer> map = new EnumMap<>(TaskStatus.class);
            for (Task task : tasks) {
                map.put(task.status, map.getOrDefault(task.status, 0) + 1);
            }
            return map;
        }

        private Task find(long id) {
            for (Task task : tasks) {
                if (task.id == id) {
                    return task;
                }
            }
            return null;
        }
    }

    private static final class TaskFileLoader {
        private TaskFileLoader() {
        }

        private static List<Task> load(String filePath) throws IOException {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new IOException("Task file not found: " + path.toAbsolutePath());
            }

            List<Task> tasks = new ArrayList<>();
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            long id = 1;
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
                tasks.add(new Task(id++, account, reunionCode));
            }
            return tasks;
        }
    }
}
