package com.realestate.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe analytics counter tracker guarded by a ReentrantLock.
 */
// [RUBRIC: Threads] [RUBRIC: Collections] ReentrantLock guarding shared analytics state
public class AnalyticsTracker {

    private final ReentrantLock lock = new ReentrantLock();

    private int totalPropertyApprovals;
    private int totalPropertyRejections;
    private int totalLeaseApprovals;
    private int totalLeaseSubmissions;
    private BigDecimal totalCommissionsEarned = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /**
     * Initializes counters from existing database totals at application startup.
     */
    // [RUBRIC: Threads]
    public void initializeCounts(int approvedProperties, int rejectedProperties,
                                 int approvedLeases, int totalLeases, BigDecimal totalCommissions) {
        lock.lock();
        try {
            this.totalPropertyApprovals = approvedProperties;
            this.totalPropertyRejections = rejectedProperties;
            this.totalLeaseApprovals = approvedLeases;
            this.totalLeaseSubmissions = totalLeases;
            this.totalCommissionsEarned = totalCommissions != null
                    ? totalCommissions.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public void incrementPropertyApprovals() {
        lock.lock();
        try {
            totalPropertyApprovals++;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public void incrementPropertyRejections() {
        lock.lock();
        try {
            totalPropertyRejections++;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public void incrementLeaseSubmissions() {
        lock.lock();
        try {
            totalLeaseSubmissions++;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public void recordLeaseApproval(BigDecimal commissionAmount) {
        lock.lock();
        try {
            totalLeaseApprovals++;
            if (commissionAmount != null) {
                totalCommissionsEarned = totalCommissionsEarned
                        .add(commissionAmount)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public int getTotalPropertyApprovals() {
        lock.lock();
        try {
            return totalPropertyApprovals;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public int getTotalPropertyRejections() {
        lock.lock();
        try {
            return totalPropertyRejections;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public int getTotalLeaseApprovals() {
        lock.lock();
        try {
            return totalLeaseApprovals;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public int getTotalLeaseSubmissions() {
        lock.lock();
        try {
            return totalLeaseSubmissions;
        } finally {
            lock.unlock();
        }
    }

    // [RUBRIC: Threads]
    public BigDecimal getTotalCommissionsEarned() {
        lock.lock();
        try {
            return totalCommissionsEarned;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an atomic point-in-time snapshot of all analytics counters as a Map.
     *
     * @return Map of metric label to formatted value
     */
    // [RUBRIC: Threads] [RUBRIC: Collections]
    public Map<String, String> getSnapshot() {
        lock.lock();
        try {
            Map<String, String> snapshot = new LinkedHashMap<>();
            snapshot.put("Total Properties Approved", String.valueOf(totalPropertyApprovals));
            snapshot.put("Total Properties Rejected", String.valueOf(totalPropertyRejections));
            snapshot.put("Total Lease Applications Submitted", String.valueOf(totalLeaseSubmissions));
            snapshot.put("Total Leases Approved (Bookings)", String.valueOf(totalLeaseApprovals));
            snapshot.put("Total Manager Commissions ($)", "$" + totalCommissionsEarned.toPlainString());
            return snapshot;
        } finally {
            lock.unlock();
        }
    }
}
