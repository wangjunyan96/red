package com.aimovies.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 数据号表: a login token (account) and its association state. */
@Entity
@Table(name = "data_accounts")
public class DataAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1024)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Enums.DataAccountStatus status = Enums.DataAccountStatus.UNLINKED;

    @Column(length = 128)
    private String reunionCode;

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Enums.DataAccountStatus getStatus() {
        return status;
    }

    public void setStatus(Enums.DataAccountStatus status) {
        this.status = status;
    }

    public String getReunionCode() {
        return reunionCode;
    }

    public void setReunionCode(String reunionCode) {
        this.reunionCode = reunionCode;
    }
}
