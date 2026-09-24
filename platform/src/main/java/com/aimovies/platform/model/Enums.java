package com.aimovies.platform.model;

/** Domain enumerations grouped in one place for readability. */
public final class Enums {
    private Enums() {
    }

    /** Account role. */
    public enum Role {
        USER,
        ADMIN
    }

    /** Whether a project is currently accepting orders (接单中/停售). */
    public enum ProjectStatus {
        ACCEPTING,
        STOPPED
    }

    /** Order lifecycle. */
    public enum OrderStatus {
        PROCESSING,
        SUCCESS,
        FAILED,
        REFUNDED
    }

    /** Worker task lifecycle. */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }

    /** Data account (数据号) association state. */
    public enum DataAccountStatus {
        UNLINKED, // 未关联
        LINKED,   // 已关联
        BOUND,    // 已绑定
        ERROR     // 错误
    }

    /** Point ledger entry type. */
    public enum PointTxType {
        ADMIN_ADJUST, // 后台调整
        CONSUME,      // 下单扣款
        REFUND        // 失败退款
    }
}
