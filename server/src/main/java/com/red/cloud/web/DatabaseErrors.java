package com.red.cloud.web;

import org.springframework.dao.DataAccessException;

/**
 * 把 JDBC 异常收成可返回给前端的短文本。
 */
public final class DatabaseErrors {
    private DatabaseErrors() {
    }

    public static String message(DataAccessException ex) {
        if (ex == null) {
            return "数据库异常";
        }
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = ex.getMessage();
        }
        if (detail == null || detail.isBlank()) {
            return "数据库异常";
        }
        String trimmed = detail.replace('\n', ' ').replace('\r', ' ').trim();
        if (trimmed.length() > 400) {
            return trimmed.substring(0, 400);
        }
        return trimmed;
    }
}
