package com.aimovies.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A purchasable project/service shown in 项目中心. */
@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String category = "未分类";

    /** Unified retail price, in points. */
    @Column(nullable = false)
    private long price;

    /** Number of data accounts (N头) required per order. */
    @Column(nullable = false)
    private int heads = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Enums.ProjectStatus status = Enums.ProjectStatus.ACCEPTING;

    @Column(length = 512)
    private String tutorialUrl;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public int getHeads() {
        return heads;
    }

    public void setHeads(int heads) {
        this.heads = heads;
    }

    public Enums.ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(Enums.ProjectStatus status) {
        this.status = status;
    }

    public String getTutorialUrl() {
        return tutorialUrl;
    }

    public void setTutorialUrl(String tutorialUrl) {
        this.tutorialUrl = tutorialUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
