package com.red.cloud.task;

import com.red.cloud.task.catalog.TaskTypeCatalog;
import com.red.cloud.task.dto.AddTaskRequest;
import com.red.cloud.task.dto.AdminTaskView;
import com.red.cloud.task.dto.BatchDeleteTasksRequest;
import com.red.cloud.task.dto.BatchRetryTasksRequest;
import com.red.cloud.task.dto.TaskClaim;
import com.red.cloud.web.DatabaseErrors;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台任务接口：按游戏/任务类型录入，供对应云手机脚本领取。
 */
@RestController
@RequestMapping("/api/v1/admin/tasks")
public class AdminTaskController {
    private static final Logger log = LoggerFactory.getLogger(AdminTaskController.class);

    private final TaskService taskService;
    private final TaskTypeCatalog catalog;

    public AdminTaskController(TaskService taskService, TaskTypeCatalog catalog) {
        this.taskService = taskService;
        this.catalog = catalog;
    }

    /** 可供新增的游戏与任务类型（含表单字段定义）。 */
    @GetMapping("/types")
    public Map<String, Object> taskTypes() {
        return Map.of("games", catalog.games());
    }

    @GetMapping
    public ResponseEntity<?> listTasks(
        @RequestParam(required = false) String gameCode,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String reunionCode
    ) {
        try {
            List<AdminTaskView> tasks = taskService.listTasks(gameCode, taskType, status, reunionCode);
            return ResponseEntity.ok(Map.of("tasks", tasks, "count", tasks.size()));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    /**
     * 新增待领取任务。结义可一次提交多个重逢码，每条分配一个 Token。
     * body: { gameCode, taskType, payload }
     */
    @PostMapping
    public ResponseEntity<?> addTask(@Valid @RequestBody AddTaskRequest request) {
        try {
            List<TaskClaim> created = taskService.addTasks(
                request.gameCode(),
                request.taskType(),
                request.payload()
            );
            List<Map<String, Object>> tasks = created.stream().map(item -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", item.id());
                payload.put("gameCode", item.gameCode());
                payload.put("taskType", item.taskType());
                payload.put("account", item.account());
                payload.put("reunionCode", item.reunionCode());
                payload.put("payload", item.payload());
                payload.put("status", "PENDING");
                return payload;
            }).toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", tasks.size());
            body.put("tasks", tasks);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody BatchDeleteTasksRequest request) {
        try {
            List<Long> ids = request == null ? List.of() : request.ids();
            int deleted = taskService.deleteByIds(ids);
            return ResponseEntity.ok(Map.of("deleted", deleted));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    /** 批量重试。body: { ids, switchToken } */
    @PostMapping("/retry")
    public ResponseEntity<?> retry(@RequestBody BatchRetryTasksRequest request) {
        try {
            List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids();
            boolean switchToken = request != null && Boolean.TRUE.equals(request.switchToken());
            int retried = taskService.retryByIds(ids, switchToken);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("retried", retried);
            body.put("switchToken", switchToken);
            return ResponseEntity.ok(body);
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    private static ResponseEntity<Map<String, String>> databaseError(DataAccessException ex) {
        log.error("任务接口数据库错误: {}", DatabaseErrors.message(ex), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", DatabaseErrors.message(ex)));
    }
}
