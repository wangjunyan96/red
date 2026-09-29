package com.red.cloud.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 首次启动写入 C 端项目、关键词。卡密不预置，由运营自行导入。
 */
@Component
@Order(3)
public class ClientSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public ClientSeedRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_c_project", Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.update(
                    "INSERT INTO t_c_project (code, name, category, price_cents, game_code, task_type, status, tutorial, sort_no, valid) "
                        + "VALUES ('hero_killer_reunion', '英雄杀-结义', '助力活动', 50, 'hero_killer', 'reunion', 'OPEN', "
                        + "'发送重逢码即可下单。系统会从英雄杀 Token 库自动分配未关联数据号，由云手机脚本完成结义。', 1, 1)"
                );
                Long projectId = jdbcTemplate.queryForObject(
                    "SELECT id FROM t_c_project WHERE code = 'hero_killer_reunion'",
                    Long.class
                );
                if (projectId != null) {
                    jdbcTemplate.update(
                        "INSERT INTO t_c_keyword (word, project_id, valid) VALUES ('结义', ?, 1), ('重逢', ?, 1), ('英雄杀', ?, 1)",
                        projectId, projectId, projectId
                    );
                }
                log.info("已初始化 C 端项目：英雄杀-结义");
            }
            Integer cards = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_c_card WHERE card_key = 'TEST10'",
                Integer.class
            );
            if (cards != null && cards == 0) {
                jdbcTemplate.update(
                    "INSERT INTO t_c_card (card_key, amount_cents, used, valid) VALUES ('TEST10', 1000, 0, 1)"
                );
                log.info("已写入体验卡密 TEST10（¥10.00）");
            }
        } catch (DataAccessException ex) {
            log.warn("跳过 C 端种子数据：{}", ex.getMessage());
        }
    }
}
