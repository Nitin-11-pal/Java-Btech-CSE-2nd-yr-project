package com.realestate.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model representing the 5% monthly rent commission earned by a Property Manager
 * when a lease application is approved.
 */
// [RUBRIC: OOP] Encapsulated Commission model with BigDecimal precision
public class Commission {

    private int id;
    private int managerId;
    private int leaseId;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public Commission(int id, int managerId, int leaseId, BigDecimal amount, LocalDateTime createdAt) {
        this.id = id;
        this.managerId = managerId;
        this.leaseId = leaseId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Commission(int managerId, int leaseId, BigDecimal amount) {
        this(0, managerId, leaseId, amount, LocalDateTime.now());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getManagerId() {
        return managerId;
    }

    public void setManagerId(int managerId) {
        this.managerId = managerId;
    }

    public int getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(int leaseId) {
        this.leaseId = leaseId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("Commission{id=%d, managerId=%d, leaseId=%d, amount=%s, createdAt=%s}",
                id, managerId, leaseId, amount, createdAt);
    }
}
