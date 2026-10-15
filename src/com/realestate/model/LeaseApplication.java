package com.realestate.model;

import java.time.LocalDate;

/**
 * Domain model representing a tenant's application to lease an approved property.
 */
// [RUBRIC: OOP] Encapsulated domain model
public class LeaseApplication {

    private int id;
    private int propertyId;
    private int tenantId;
    private LocalDate moveInDate;
    private String message;
    private LeaseStatus status;

    public LeaseApplication(int id, int propertyId, int tenantId, LocalDate moveInDate,
                            String message, LeaseStatus status) {
        this.id = id;
        this.propertyId = propertyId;
        this.tenantId = tenantId;
        this.moveInDate = moveInDate;
        this.message = message;
        this.status = status;
    }

    public LeaseApplication(int propertyId, int tenantId, LocalDate moveInDate, String message) {
        this(0, propertyId, tenantId, moveInDate, message, LeaseStatus.PENDING);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(int propertyId) {
        this.propertyId = propertyId;
    }

    public int getTenantId() {
        return tenantId;
    }

    public void setTenantId(int tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDate getMoveInDate() {
        return moveInDate;
    }

    public void setMoveInDate(LocalDate moveInDate) {
        this.moveInDate = moveInDate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public void setStatus(LeaseStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("LeaseApplication{id=%d, propertyId=%d, tenantId=%d, moveInDate=%s, status=%s}",
                id, propertyId, tenantId, moveInDate, status);
    }
}
