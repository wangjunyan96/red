package com.red.cloud.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时若任务表为空，则用 data/tasks.csv 做一次性种子导入。
 * CSV 列：account(token), reunionCode。
 */
@Component
@Order(2)
public class TaskSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskSeedRunner.class);

    private final TaskService taskService;
    private final String seedFile;

    public TaskSeedRunner(
        TaskService taskService,
        @Value("${app.task.seed-file:data/tasks.csv}") String seedFile
    ) {
        this.taskService = taskService;
        this.seedFile = seedFile;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!taskService.listTasks().isEmpty()) {
                log.info("任务表已有数据，跳过 CSV 种子导入。leaseSeconds={}", taskService.leaseSeconds());
                return;
            }
        } catch (DataAccessException ex) {
            log.warn("数据库不可用，跳过 CSV 种子导入。原因: {}", ex.getMessage());
            return;
        }

        Path path = Path.of(seedFile);
        if (!Files.exists(path)) {
            log.info("未找到种子文件 {}，请通过 POST /api/v1/admin/tasks 录入 token 和重逢码。", path.toAbsolutePath());
            return;
        }

        try {
            int inserted = 0;
            for (SeedRow row : loadSeedRows(path)) {
                taskService.addTask(row.account(), row.reunionCode());
                inserted++;
            }
            log.info("已从 {} 导入 {} 条任务，leaseSeconds={}", path.toAbsolutePath(), inserted, taskService.leaseSeconds());
        } catch (IOException ex) {
            log.warn("读取种子文件失败: {}", ex.getMessage());
        }
    }

    private static List<SeedRow> loadSeedRows(Path path) throws IOException {
        List<SeedRow> rows = new ArrayList<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
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

    private record SeedRow(String account, String reunionCode) {
    }
}
