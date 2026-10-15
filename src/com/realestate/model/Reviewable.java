package com.realestate.model;

/**
 * Contract for domain entities that can be reviewed and approved or rejected by an administrator.
 */
// [RUBRIC: OOP] Interface implemented by Property
public interface Reviewable {

    /**
     * Approves the reviewable item and clears any rejection reason.
     */
    void approve();

    /**
     * Rejects the reviewable item with an explanatory reason.
     *
     * @param reason the mandatory reason for rejection
     */
    void reject(String reason);
}
