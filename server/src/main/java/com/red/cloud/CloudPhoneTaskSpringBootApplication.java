package com.red.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 云手机任务服务的 Spring Boot 启动入口。
 * 会扫描本包及子包中的 Controller、Service 等组件。
 */
@SpringBootApplication
public class CloudPhoneTaskSpringBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudPhoneTaskSpringBootApplication.class, args);
    }
}
