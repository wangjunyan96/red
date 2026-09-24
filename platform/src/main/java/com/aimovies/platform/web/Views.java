package com.aimovies.platform.web;

import com.aimovies.platform.model.OrderEntity;
import com.aimovies.platform.model.User;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps entities to safe JSON views (e.g. hides password). */
public final class Views {
    private Views() {
    }

    public static Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        m.put("points", u.getPoints());
        return m;
    }

    public static Map<String, Object> order(OrderEntity o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("projectName", o.getProjectName());
        m.put("inviteCode", o.getInviteCode());
        m.put("amount", o.getAmount());
        m.put("heads", o.getHeads());
        m.put("status", o.getStatus());
        m.put("error", o.getError());
        m.put("createdAt", o.getCreatedAt());
        m.put("finishedAt", o.getFinishedAt());
        return m;
    }
}
