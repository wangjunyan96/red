package com.aimovies.platform.web;

import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.Task;
import com.aimovies.platform.service.TaskService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Worker-facing dispatch API, compatible with the existing Auto.js clients. */
@RestController
@RequestMapping("/api/v1")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "time", Instant.now().toString());
    }

    @PostMapping("/tasks/claim")
    public Map<String, Object> claim(@RequestBody Dtos.DeviceRequest req) {
        if (req.deviceId() == null || req.deviceId().isBlank()) {
            throw ApiException.badRequest("deviceId is required");
        }
        Task task = taskService.claim(req.deviceId().trim());
        Map<String, Object> result = new LinkedHashMap<>();
        if (task == null) {
            result.put("task", null);
            return result;
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", task.getId());
        t.put("account", task.getAccount());
        t.put("reunionCode", task.getReunionCode());
        t.put("runId", task.getRunId());
        result.put("task", t);
        return result;
    }

    @PostMapping("/tasks/{id}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable Long id, @RequestBody Dtos.HeartbeatRequest req) {
        requireDeviceRun(req.deviceId(), req.runId());
        return Map.of("ok", taskService.heartbeat(id, req.deviceId().trim(), req.runId().trim()));
    }

    @PostMapping("/tasks/{id}/report")
    public Map<String, Object> report(@PathVariable Long id, @RequestBody Dtos.ReportRequest req) {
        requireDeviceRun(req.deviceId(), req.runId());
        if (req.status() == null || req.status().isBlank()) {
            throw ApiException.badRequest("status is required");
        }
        boolean ok = taskService.report(id, req.deviceId().trim(), req.runId().trim(),
            req.status().trim(), req.error());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        if (!ok) {
            result.put("error", "invalid task state or status");
        }
        return result;
    }

    @GetMapping("/tasks/stats")
    public Map<String, Object> stats() {
        Map<Enums.TaskStatus, Long> s = taskService.stats();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", s.getOrDefault(Enums.TaskStatus.PENDING, 0L));
        result.put("running", s.getOrDefault(Enums.TaskStatus.RUNNING, 0L));
        result.put("done", s.getOrDefault(Enums.TaskStatus.DONE, 0L));
        result.put("failed", s.getOrDefault(Enums.TaskStatus.FAILED, 0L));
        return result;
    }

    private void requireDeviceRun(String deviceId, String runId) {
        if (deviceId == null || deviceId.isBlank() || runId == null || runId.isBlank()) {
            throw ApiException.badRequest("deviceId and runId are required");
        }
    }
}
