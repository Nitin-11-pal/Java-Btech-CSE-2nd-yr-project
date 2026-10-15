package com.realestate.ui;

import com.realestate.exception.InvalidPropertyDataException;
import com.realestate.exception.InvalidUserDataException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedActionException;
import com.realestate.model.AuditLog;
import com.realestate.model.Property;
import com.realestate.model.User;
import com.realestate.service.AuthService;
import com.realestate.service.RealEstateService;
import com.realestate.util.Result;
import com.realestate.util.SessionManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Administrator Swing dashboard with tabs for User Management, Property Moderation,
 * and Audit Log + Thread-Safe Analytics inspection.
 */
// [RUBRIC: OOP] [RUBRIC: Threads] [RUBRIC: Collections]
public class AdminDashboard extends JPanel {

    private final MainFrame mainFrame;
    private final AuthService authService;
    private final RealEstateService realEstateService;

    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Tab 1: Users table & controls
    private final DefaultTableModel usersTableModel = new DefaultTableModel(
            new Object[]{"ID", "Name", "Email", "Role", "Active"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable usersTable = new JTable(usersTableModel);
    private final JButton deactivateUserButton = new JButton("Deactivate / Delete Selected User");
    private final JButton reactivateUserButton = new JButton("Reactivate Selected User");

    // Tab 2: Properties moderation table & controls
    private final DefaultTableModel propertiesTableModel = new DefaultTableModel(
            new Object[]{"ID", "Manager ID", "Title", "City", "Address", "Monthly Rent ($)", "Bedrooms", "Status", "Rejection Reason"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable propertiesTable = new JTable(propertiesTableModel);
    private final JCheckBox pendingOnlyCheckBox = new JCheckBox("Show PENDING Only", true);
    private final JButton approvePropertyButton = new JButton("Approve Selected Property");
    private final JButton rejectPropertyButton = new JButton("Reject Selected Property");

    // Tab 3: Analytics + Audit Log tables
    private final DefaultTableModel analyticsTableModel = new DefaultTableModel(
            new Object[]{"Analytics Metric", "Current Value"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable analyticsTable = new JTable(analyticsTableModel);

    private final DefaultTableModel auditTableModel = new DefaultTableModel(
            new Object[]{"Log ID", "User ID", "Action", "Details", "Timestamp"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable auditTable = new JTable(auditTableModel);

    public AdminDashboard(MainFrame mainFrame, AuthService authService, RealEstateService realEstateService) {
        this.mainFrame = mainFrame;
        this.authService = authService;
        this.realEstateService = realEstateService;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tabbedPane.addTab("Manage Users", buildUsersTab());
        tabbedPane.addTab("Review Properties", buildPropertiesTab());
        tabbedPane.addTab("Audit Log & Analytics", buildAuditAndAnalyticsTab());

        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx == 0) {
                refreshUsersTab();
            } else if (idx == 1) {
                refreshPropertiesTab();
            } else if (idx == 2) {
                refreshAuditAndAnalyticsTab();
            }
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel buildUsersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        usersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton refreshButton = new JButton("Refresh Users");

        deactivateUserButton.addActionListener(e -> handleUserStatusChange(false));
        reactivateUserButton.addActionListener(e -> handleUserStatusChange(true));
        refreshButton.addActionListener(e -> refreshUsersTab());

        buttonBar.add(deactivateUserButton);
        buttonBar.add(reactivateUserButton);
        buttonBar.add(refreshButton);

        panel.add(buttonBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPropertiesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        pendingOnlyCheckBox.addActionListener(e -> refreshPropertiesTab());
        topBar.add(pendingOnlyCheckBox);
        panel.add(topBar, BorderLayout.NORTH);

        propertiesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(propertiesTable), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton refreshButton = new JButton("Refresh Properties");

        approvePropertyButton.addActionListener(e -> handleApproveProperty());
        rejectPropertyButton.addActionListener(e -> handleRejectProperty());
        refreshButton.addActionListener(e -> refreshPropertiesTab());

        buttonBar.add(approvePropertyButton);
        buttonBar.add(rejectPropertyButton);
        buttonBar.add(refreshButton);

        panel.add(buttonBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAuditAndAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel analyticsPanel = new JPanel(new BorderLayout());
        analyticsPanel.setBorder(BorderFactory.createTitledBorder("System Analytics (Thread-Safe Counters)"));
        analyticsPanel.add(new JScrollPane(analyticsTable), BorderLayout.CENTER);

        JPanel auditPanel = new JPanel(new BorderLayout());
        auditPanel.setBorder(BorderFactory.createTitledBorder("Audit Log Table (Persisted by Daemon AuditWorker)"));
        auditPanel.add(new JScrollPane(auditTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, analyticsPanel, auditPanel);
        splitPane.setDividerLocation(160);
        panel.add(splitPane, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton refreshButton = new JButton("Refresh Audit Log & Analytics");
        refreshButton.addActionListener(e -> refreshAuditAndAnalyticsTab());
        buttonBar.add(refreshButton);
        panel.add(buttonBar, BorderLayout.SOUTH);

        return panel;
    }

    public void refreshAllTabs() {
        refreshUsersTab();
        refreshPropertiesTab();
        refreshAuditAndAnalyticsTab();
    }

    // [RUBRIC: Threads] SwingWorker for non-blocking DB query
    private void refreshUsersTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        SwingWorker<Result<List<User>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<List<User>> doInBackground() {
                try {
                    return Result.ok(authService.getAllUsers(actor));
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<List<User>> result = get();
                    if (result.isSuccess()) {
                        usersTableModel.setRowCount(0);
                        for (User u : result.getData()) {
                            usersTableModel.addRow(new Object[]{
                                    u.getId(),
                                    u.getName(),
                                    u.getEmail(),
                                    u.getRole(),
                                    u.isActive() ? "Active" : "Deactivated"
                            });
                        }
                    } else {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                result.getErrorMessage(), "Error Loading Users", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Failed to load users: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleUserStatusChange(boolean activate) {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user row first.",
                    "No User Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        final int targetUserId = (Integer) usersTableModel.getValueAt(modelRow, 0);
        final User actor = SessionManager.getInstance().getCurrentUser();

        deactivateUserButton.setEnabled(false);
        reactivateUserButton.setEnabled(false);

        SwingWorker<Result<Void>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Void> doInBackground() {
                try {
                    if (activate) {
                        authService.setUserActiveStatus(actor, targetUserId, true);
                    } else {
                        authService.deactivateUser(actor, targetUserId);
                    }
                    return Result.ok(null);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidUserDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Void> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                activate ? "User reactivated and login lockout cleared." : "User deactivated (soft delete).",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                        refreshUsersTab();
                        refreshAuditAndAnalyticsTab();
                    } else {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                result.getErrorMessage(), "Action Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    deactivateUserButton.setEnabled(true);
                    reactivateUserButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads]
    private void refreshPropertiesTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }
        final boolean pendingOnly = pendingOnlyCheckBox.isSelected();

        SwingWorker<Result<List<Property>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<List<Property>> doInBackground() {
                try {
                    List<Property> list = pendingOnly
                            ? realEstateService.getPendingProperties(actor)
                            : realEstateService.getAllProperties(actor);
                    return Result.ok(list);
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<List<Property>> result = get();
                    if (result.isSuccess()) {
                        propertiesTableModel.setRowCount(0);
                        for (Property p : result.getData()) {
                            propertiesTableModel.addRow(new Object[]{
                                    p.getId(),
                                    p.getManagerId(),
                                    p.getTitle(),
                                    p.getCity(),
                                    p.getAddress(),
                                    p.getMonthlyRent().toPlainString(),
                                    p.getBedrooms(),
                                    p.getStatus(),
                                    p.getRejectionReason() != null ? p.getRejectionReason() : ""
                            });
                        }
                    } else {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                result.getErrorMessage(), "Error Loading Properties", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Failed to load properties: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleApproveProperty() {
        int selectedRow = propertiesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a property row to approve.",
                    "No Property Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = propertiesTable.convertRowIndexToModel(selectedRow);
        final int propertyId = (Integer) propertiesTableModel.getValueAt(modelRow, 0);
        final User actor = SessionManager.getInstance().getCurrentUser();

        approvePropertyButton.setEnabled(false);
        rejectPropertyButton.setEnabled(false);

        SwingWorker<Result<Void>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Void> doInBackground() {
                try {
                    realEstateService.approveProperty(actor, propertyId);
                    return Result.ok(null);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidPropertyDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Void> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                "Property #" + propertyId + " approved.",
                                "Property Approved", JOptionPane.INFORMATION_MESSAGE);
                        refreshPropertiesTab();
                        refreshAuditAndAnalyticsTab();
                    } else {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                result.getErrorMessage(), "Approval Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    approvePropertyButton.setEnabled(true);
                    rejectPropertyButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleRejectProperty() {
        int selectedRow = propertiesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a property row to reject.",
                    "No Property Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = propertiesTable.convertRowIndexToModel(selectedRow);
        final int propertyId = (Integer) propertiesTableModel.getValueAt(modelRow, 0);
        final String reason = JOptionPane.showInputDialog(
                this,
                "Enter mandatory rejection reason for Property #" + propertyId + ":",
                "Reject Property",
                JOptionPane.QUESTION_MESSAGE
        );
        if (reason == null) {
            return;
        }

        final User actor = SessionManager.getInstance().getCurrentUser();

        approvePropertyButton.setEnabled(false);
        rejectPropertyButton.setEnabled(false);

        SwingWorker<Result<Void>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Void> doInBackground() {
                try {
                    realEstateService.rejectProperty(actor, propertyId, reason);
                    return Result.ok(null);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidPropertyDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Void> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                "Property #" + propertyId + " rejected.",
                                "Property Rejected", JOptionPane.INFORMATION_MESSAGE);
                        refreshPropertiesTab();
                        refreshAuditAndAnalyticsTab();
                    } else {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                result.getErrorMessage(), "Rejection Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    approvePropertyButton.setEnabled(true);
                    rejectPropertyButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads]
    private void refreshAuditAndAnalyticsTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        SwingWorker<Result<List<AuditLog>>, Void> worker = new SwingWorker<>() {
            private Map<String, String> snapshot;

            @Override
            protected Result<List<AuditLog>> doInBackground() {
                try {
                    snapshot = realEstateService.getAnalyticsSnapshot(actor);
                    List<AuditLog> logs = realEstateService.getAuditLogs(actor);
                    return Result.ok(logs);
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<List<AuditLog>> result = get();
                    if (result.isSuccess()) {
                        analyticsTableModel.setRowCount(0);
                        if (snapshot != null) {
                            for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                                analyticsTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
                            }
                        }

                        auditTableModel.setRowCount(0);
                        for (AuditLog log : result.getData()) {
                            auditTableModel.addRow(new Object[]{
                                    log.getId(),
                                    log.getUserId() != null ? log.getUserId() : "N/A",
                                    log.getAction(),
                                    log.getDetails(),
                                    log.getCreatedAt()
                            });
                        }
                    } else {
                        JOptionPane.showMessageDialog(AdminDashboard.this,
                                result.getErrorMessage(), "Error Loading Audit/Analytics", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Failed to load audit log: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
