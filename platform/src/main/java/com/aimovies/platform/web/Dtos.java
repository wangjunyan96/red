package com.aimovies.platform.web;

import java.util.List;

/** Request payloads, grouped as records. */
public final class Dtos {
    private Dtos() {
    }

    public record RegisterRequest(String username, String password) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record OrderRequest(Long projectId, String code) {
    }

    public record AdminPointsRequest(Long delta, Long set, String remark) {
    }

    public record ProjectRequest(String name, String category, Long price, Integer heads,
                                 String status, String tutorialUrl) {
    }

    public record DataAccountsRequest(List<String> tokens) {
    }

    public record DeviceRequest(String deviceId) {
    }

    public record HeartbeatRequest(String deviceId, String runId) {
    }

    public record ReportRequest(String deviceId, String runId, String status, String error) {
    }
}
