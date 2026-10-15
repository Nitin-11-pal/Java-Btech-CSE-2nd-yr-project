package com.realestate.model;

import java.time.LocalDateTime;

/**
 * Domain model representing an asynchronous system audit log entry.
 */
// [RUBRIC: OOP] Encapsulated AuditLog model
public class AuditLog {

    private int id;
    private Integer userId;
    private String action;
    private String details;
    private LocalDateTime createdAt;

    public AuditLog(int id, Integer userId, String action, String details, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public AuditLog(Integer userId, String action, String details) {
        this(0, userId, action, details, LocalDateTime.now());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("AuditLog{id=%d, userId=%s, action='%s', details='%s', createdAt=%s}",
                id, userId, action, details, createdAt);
    }
}
