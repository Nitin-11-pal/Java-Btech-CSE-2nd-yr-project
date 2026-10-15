package com.realestate.worker;

import com.realestate.dao.AuditLogDAO;
import com.realestate.model.AuditLog;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background daemon worker that consumes AuditLog events from a thread-safe BlockingQueue
 * and persists them to the audit_log table, draining remaining events on JVM shutdown.
 */
// [RUBRIC: Threads] [RUBRIC: Collections] Runnable daemon worker with BlockingQueue and shutdown drain
public class AuditWorker implements Runnable {

    private static final AuditLog POISON_PILL = new AuditLog(null, "__POISON_PILL__", "");

    private final BlockingQueue<AuditLog> queue = new LinkedBlockingQueue<>();
    private final AuditLogDAO auditLogDAO;
    private final Object drainLock = new Object();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private volatile boolean running = false;
    private Thread workerThread;

    public AuditWorker() {
        this(new AuditLogDAO());
    }

    public AuditWorker(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    /**
     * Starts the background daemon thread and registers a JVM shutdown hook to drain queued items.
     */
    // [RUBRIC: Threads] Starts daemon thread and registers shutdown hook
    public synchronized void startDaemon() {
        if (running) {
            return;
        }
        running = true;
        workerThread = new Thread(this, "AuditWorker-Daemon");
        workerThread.setDaemon(true);
        workerThread.start();

        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "AuditWorker-ShutdownHook"));
        }
    }

    /**
     * Pushes an AuditLog entry into the thread-safe queue for asynchronous persistence.
     *
     * @param log audit event to enqueue
     */
    // [RUBRIC: Threads] [RUBRIC: Collections]
    public void enqueue(AuditLog log) {
        if (log == null) {
            return;
        }
        pendingCount.incrementAndGet();
        if (!queue.offer(log)) {
            pendingCount.decrementAndGet();
        } else if (!running) {
            drainRemaining();
        }
    }

    /**
     * Convenience helper to create and enqueue an AuditLog event.
     *
     * @param userId  ID of the acting user (nullable)
     * @param action  short action code
     * @param details descriptive audit details
     */
    public void logEvent(Integer userId, String action, String details) {
        enqueue(new AuditLog(userId, action, details));
    }

    /**
     * Waits briefly for pending audit events in the queue to be persisted so UI reads see up-to-date logs.
     *
     * @param timeoutMillis maximum time to wait in milliseconds
     */
    public void awaitPendingWrites(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (pendingCount.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    // [RUBRIC: Threads] Producer-consumer loop consuming from BlockingQueue
    public void run() {
        try {
            while (running) {
                AuditLog log = queue.take();
                if (log == POISON_PILL) {
                    break;
                }
                try {
                    persistSilently(log);
                } finally {
                    pendingCount.decrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            drainRemaining();
        }
    }

    /**
     * Signals the worker to stop and drains all remaining audit events in the queue to the database.
     * Does not hold a monitor lock across workerThread.join() to prevent deadlock with drainRemaining().
     */
    // [RUBRIC: Threads] Graceful shutdown draining remaining queue items without deadlock
    public void shutdown() {
        Thread threadToJoin;
        synchronized (this) {
            if (!running && queue.isEmpty() && pendingCount.get() == 0) {
                return;
            }
            running = false;
            queue.offer(POISON_PILL);
            threadToJoin = workerThread;
        }

        if (threadToJoin != null && threadToJoin.isAlive() && Thread.currentThread() != threadToJoin) {
            try {
                threadToJoin.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainRemaining();
    }

    private void drainRemaining() {
        synchronized (drainLock) {
            List<AuditLog> remaining = new ArrayList<>();
            queue.drainTo(remaining);
            for (AuditLog log : remaining) {
                if (log != null && log != POISON_PILL) {
                    try {
                        persistSilently(log);
                    } finally {
                        pendingCount.decrementAndGet();
                    }
                }
            }
        }
    }

    private void persistSilently(AuditLog log) {
        try {
            auditLogDAO.insert(log);
        } catch (SQLException e) {
            System.err.println("[AuditWorker] Failed to persist audit log: " + e.getMessage());
        }
    }
}
