package com.aimovies.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 重逢码表: a reunion code and how many accounts are bound to it. */
@Entity
@Table(name = "reunion_codes")
public class ReunionCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String reunionCode;

    @Column(nullable = false)
    private int bindCount = 0;

    public ReunionCode() {
    }

    public ReunionCode(String reunionCode) {
        this.reunionCode = reunionCode;
    }

    public Long getId() {
        return id;
    }

    public String getReunionCode() {
        return reunionCode;
    }

    public void setReunionCode(String reunionCode) {
        this.reunionCode = reunionCode;
    }

    public int getBindCount() {
        return bindCount;
    }

    public void setBindCount(int bindCount) {
        this.bindCount = bindCount;
    }
}
