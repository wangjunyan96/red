package com.red.cloud.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 启动后尝试执行 schema.sql。
 * MySQL 连不上时只打日志，不阻止服务启动。
 */
@Component
@Order(1)
public class OptionalSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OptionalSchemaInitializer.class);

    private final DataSource dataSource;

    public OptionalSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection ignored = dataSource.getConnection()) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.setContinueOnError(true);
            DatabasePopulatorUtils.execute(populator, dataSource);
            log.info("MySQL 已连接，已执行 schema.sql");
        } catch (Exception ex) {
            log.warn("MySQL 暂不可用，服务仍继续启动。登录/任务接口会失败，直到数据库恢复。原因: {}", ex.getMessage());
        }
    }
}
