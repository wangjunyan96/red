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

/** A placed order. Table is named "orders" (ORDER is a reserved word). */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user", columnList = "userId"),
    @Index(name = "idx_orders_no", columnList = "orderNo", unique = true)
})
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 128)
    private String projectName;

    @Column(nullable = false, length = 128)
    private String inviteCode;

    /** Points deducted for this order. */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private int heads;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Enums.OrderStatus status = Enums.OrderStatus.PROCESSING;

    @Column(length = 512)
    private String error;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant finishedAt;

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public int getHeads() {
        return heads;
    }

    public void setHeads(int heads) {
        this.heads = heads;
    }

    public Enums.OrderStatus getStatus() {
        return status;
    }

    public void setStatus(Enums.OrderStatus status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
