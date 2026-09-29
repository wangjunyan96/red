package com.red.cloud.task;

import com.red.cloud.task.dto.ClaimRequest;
import com.red.cloud.task.dto.HeartbeatRequest;
import com.red.cloud.task.dto.ReportRequest;
import com.red.cloud.task.dto.TaskClaim;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云手机脚本接口。
 * Auto.js 通过这里按 gameCode + taskType 领取任务，并回传执行结果。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 领取下一条匹配类型的任务。
     * 有任务时返回 {task:{id,gameCode,taskType,account,reunionCode,payload,runId}}；
     * 队列为空时返回 {task:null}，脚本会继续轮询。
     */
    @PostMapping("/claim")
    public ResponseEntity<?> claim(@Valid @RequestBody ClaimRequest request) {
        try {
            TaskClaim task = taskService.claim(
                request.deviceId().trim(),
                request.gameCode(),
                request.taskType()
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("task", task == null ? null : toPayload(task));
            return ResponseEntity.ok(body);
        } catch (DataAccessException ex) {
            return databaseError();
        }
    }

    /** 心跳续租，避免任务因超时被其它设备抢走。 */
    @PostMapping("/{taskId}/heartbeat")
    public ResponseEntity<?> heartbeat(
        @PathVariable("taskId") long taskId,
        @Valid @RequestBody HeartbeatRequest request
    ) {
        try {
            boolean ok = taskService.heartbeat(taskId, request.deviceId().trim(), request.runId().trim());
            return ResponseEntity.ok(Map.of("ok", ok));
        } catch (DataAccessException ex) {
            return databaseError();
        }
    }

    /** 上报 done/failed。状态不合法或任务已不属于该设备时返回 400。 */
    @PostMapping("/{taskId}/report")
    public ResponseEntity<?> report(
        @PathVariable("taskId") long taskId,
        @Valid @RequestBody ReportRequest request
    ) {
        try {
            boolean ok = taskService.report(
                taskId,
                request.deviceId().trim(),
                request.runId().trim(),
                request.status(),
                emptyToNull(request.error())
            );
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("ok", false, "error", "invalid task state or status"));
            }
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (DataAccessException ex) {
            return databaseError();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        try {
            Map<TaskStatus, Integer> stats = taskService.stats();
            Map<String, Integer> body = new LinkedHashMap<>();
            body.put("pending", stats.getOrDefault(TaskStatus.PENDING, 0));
            body.put("running", stats.getOrDefault(TaskStatus.RUNNING, 0));
            body.put("done", stats.getOrDefault(TaskStatus.DONE, 0));
            body.put("failed", stats.getOrDefault(TaskStatus.FAILED, 0));
            return ResponseEntity.ok(body);
        } catch (DataAccessException ex) {
            return databaseError();
        }
    }

    private static Map<String, Object> toPayload(TaskClaim task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", task.id());
        payload.put("gameCode", task.gameCode());
        payload.put("taskType", task.taskType());
        payload.put("account", task.account());
        payload.put("reunionCode", task.reunionCode());
        payload.put("payload", task.payload());
        payload.put("runId", task.runId());
        return payload;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseEntity<Map<String, String>> databaseError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "database error"));
    }
}
