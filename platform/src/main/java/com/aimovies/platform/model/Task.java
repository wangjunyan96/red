package com.aimovies.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** One unit of worker execution (one data account filling one invite code). */
@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_tasks_order", columnList = "orderId"),
    @Index(name = "idx_tasks_status", columnList = "status")
})
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    /** Data account token used to log in (数据号). */
    @Column(nullable = false, length = 1024)
    private String account;

    @Column(nullable = false, length = 128)
    private String reunionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Enums.TaskStatus status = Enums.TaskStatus.PENDING;

    @Column(length = 128)
    private String assignedDevice;

    @Column(length = 64)
    private String runId;

    @Column(nullable = false)
    private long leaseUntil = 0L;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 512)
    private String lastError;

    /** Data account id reserved for this task, so we can update its state. */
    @Column(nullable = false)
    private Long dataAccountId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getReunionCode() {
        return reunionCode;
    }

    public void setReunionCode(String reunionCode) {
        this.reunionCode = reunionCode;
    }

    public Enums.TaskStatus getStatus() {
        return status;
    }

    public void setStatus(Enums.TaskStatus status) {
        this.status = status;
    }

    public String getAssignedDevice() {
        return assignedDevice;
    }

    public void setAssignedDevice(String assignedDevice) {
        this.assignedDevice = assignedDevice;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public long getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(long leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Long getDataAccountId() {
        return dataAccountId;
    }

    public void setDataAccountId(Long dataAccountId) {
        this.dataAccountId = dataAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
