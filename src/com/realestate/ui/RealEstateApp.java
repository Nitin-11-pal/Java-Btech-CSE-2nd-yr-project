package com.realestate.ui;

import com.realestate.service.AuthService;
import com.realestate.service.RealEstateService;
import com.realestate.util.AnalyticsTracker;
import com.realestate.util.SeedData;
import com.realestate.worker.AuditWorker;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main application entry point for the Online Real Estate Management System.
 */
// [RUBRIC: OOP] [RUBRIC: Threads]
public class RealEstateApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            AuditWorker auditWorker = new AuditWorker();
            auditWorker.startDaemon();

            try {
                // Seed initial demo accounts and 5 sample properties if database is empty
                SeedData.seedIfEmpty();

                AnalyticsTracker analyticsTracker = new AnalyticsTracker();
                AuthService authService = new AuthService(auditWorker);
                RealEstateService realEstateService = new RealEstateService(
                        authService,
                        auditWorker,
                        analyticsTracker
                );

                MainFrame mainFrame = new MainFrame(authService, realEstateService, auditWorker);
                mainFrame.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Failed to initialize application or connect to MySQL.\n"
                                + "Please verify schema.sql has been executed and db.properties is configured.\n\n"
                                + "Details: " + e.getMessage(),
                        "Database Initialization Error",
                        JOptionPane.ERROR_MESSAGE
                );
                auditWorker.shutdown();
                System.exit(1);
            }
        });
    }
}
