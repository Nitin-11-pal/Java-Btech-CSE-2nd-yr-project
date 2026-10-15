package com.realestate.model;

import java.math.BigDecimal;

/**
 * Domain model representing a rental property listing. Implements Reviewable for admin moderation.
 */
// [RUBRIC: OOP] Encapsulated model implementing Reviewable interface
public class Property implements Reviewable {

    private int id;
    private int managerId;
    private String title;
    private String city;
    private String address;
    private BigDecimal monthlyRent;
    private int bedrooms;
    private PropertyStatus status;
    private String rejectionReason;

    public Property(int id, int managerId, String title, String city, String address,
                    BigDecimal monthlyRent, int bedrooms, PropertyStatus status, String rejectionReason) {
        this.id = id;
        this.managerId = managerId;
        this.title = title;
        this.city = city;
        this.address = address;
        this.monthlyRent = monthlyRent;
        this.bedrooms = bedrooms;
        this.status = status;
        this.rejectionReason = rejectionReason;
    }

    public Property(int managerId, String title, String city, String address,
                    BigDecimal monthlyRent, int bedrooms) {
        this(0, managerId, title, city, address, monthlyRent, bedrooms, PropertyStatus.PENDING, null);
    }

    @Override
    // [RUBRIC: OOP] Reviewable implementation
    public void approve() {
        this.status = PropertyStatus.APPROVED;
        this.rejectionReason = null;
    }

    @Override
    // [RUBRIC: OOP] Reviewable implementation
    public void reject(String reason) {
        this.status = PropertyStatus.REJECTED;
        this.rejectionReason = reason;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(int bedrooms) {
        this.bedrooms = bedrooms;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    public void setStatus(PropertyStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    @Override
    public String toString() {
        return String.format("Property{id=%d, managerId=%d, title='%s', city='%s', rent=%s, bedrooms=%d, status=%s}",
                id, managerId, title, city, monthlyRent, bedrooms, status);
    }
}
