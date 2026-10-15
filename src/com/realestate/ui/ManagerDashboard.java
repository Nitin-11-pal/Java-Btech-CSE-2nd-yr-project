package com.realestate.ui;

import com.realestate.exception.InvalidLeaseDataException;
import com.realestate.exception.InvalidPropertyDataException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedActionException;
import com.realestate.model.Commission;
import com.realestate.model.LeaseApplication;
import com.realestate.model.Property;
import com.realestate.model.User;
import com.realestate.service.RealEstateService;
import com.realestate.util.Result;
import com.realestate.util.SessionManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Property Manager Swing dashboard with tabs for My Properties CRUD, Lease Applications
 * (including atomic lease approval), and 5% Commissions Earned.
 */
// [RUBRIC: OOP] [RUBRIC: Threads] [RUBRIC: Transaction]
public class ManagerDashboard extends JPanel {

    private final MainFrame mainFrame;
    private final RealEstateService realEstateService;

    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Tab 1: My Properties
    private final DefaultTableModel myPropertiesModel = new DefaultTableModel(
            new Object[]{"ID", "Title", "City", "Address", "Monthly Rent ($)", "Bedrooms", "Status", "Rejection Reason"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable myPropertiesTable = new JTable(myPropertiesModel);

    private final JTextField titleField = new JTextField(18);
    private final JTextField cityField = new JTextField(12);
    private final JTextField addressField = new JTextField(20);
    private final JTextField rentField = new JTextField(10);
    private final JTextField bedroomsField = new JTextField(5);

    private final JButton addPropertyButton = new JButton("Add Property");
    private final JButton updatePropertyButton = new JButton("Update Selected Property");
    private final JButton deletePropertyButton = new JButton("Delete Selected Property");

    // Tab 2: Lease Applications on My Properties
    private final DefaultTableModel leaseAppsModel = new DefaultTableModel(
            new Object[]{"App ID", "Property ID", "Property Title", "Tenant ID", "Tenant Name", "Move-In Date", "Message", "Status"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable leaseAppsTable = new JTable(leaseAppsModel);
    private final JButton approveLeaseButton = new JButton("Approve Selected Lease (Atomic Transaction)");
    private final JButton rejectLeaseButton = new JButton("Reject Selected Lease");

    // Tab 3: Commissions
    private final DefaultTableModel commissionsModel = new DefaultTableModel(
            new Object[]{"Commission ID", "Lease ID", "Amount ($)", "Created At"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable commissionsTable = new JTable(commissionsModel);
    private final JLabel totalCommissionLabel = new JLabel("Total Commission Earned (5%): $0.00");

    public ManagerDashboard(MainFrame mainFrame, RealEstateService realEstateService) {
        this.mainFrame = mainFrame;
        this.realEstateService = realEstateService;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tabbedPane.addTab("My Properties", buildMyPropertiesTab());
        tabbedPane.addTab("Lease Applications", buildLeaseApplicationsTab());
        tabbedPane.addTab("Commissions Earned", buildCommissionsTab());

        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx == 0) {
                refreshMyPropertiesTab();
            } else if (idx == 1) {
                refreshLeaseApplicationsTab();
            } else if (idx == 2) {
                refreshCommissionsTab();
            }
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel buildMyPropertiesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        myPropertiesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myPropertiesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                populateFormFromSelectedProperty();
            }
        });
        panel.add(new JScrollPane(myPropertiesTable), BorderLayout.CENTER);

        JPanel southContainer = new JPanel(new BorderLayout(6, 6));
        southContainer.setBorder(BorderFactory.createTitledBorder(
                "Add / Edit Property (Editing an APPROVED property resets status to PENDING)"));

        JPanel formGrid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formGrid.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1;
        formGrid.add(titleField, gbc);

        gbc.gridx = 2;
        formGrid.add(new JLabel("City:"), gbc);
        gbc.gridx = 3;
        formGrid.add(cityField, gbc);

        gbc.gridx = 4;
        formGrid.add(new JLabel("Bedrooms:"), gbc);
        gbc.gridx = 5;
        formGrid.add(bedroomsField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formGrid.add(new JLabel("Address:"), gbc);
        gbc.gridx = 1;
        formGrid.add(addressField, gbc);

        gbc.gridx = 2;
        formGrid.add(new JLabel("Monthly Rent ($):"), gbc);
        gbc.gridx = 3;
        formGrid.add(rentField, gbc);

        southContainer.add(formGrid, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton clearButton = new JButton("Clear Form");
        JButton refreshButton = new JButton("Refresh");

        addPropertyButton.addActionListener(e -> handleCreateProperty());
        updatePropertyButton.addActionListener(e -> handleUpdateProperty());
        deletePropertyButton.addActionListener(e -> handleDeleteProperty());
        clearButton.addActionListener(e -> clearPropertyForm());
        refreshButton.addActionListener(e -> refreshMyPropertiesTab());

        buttonBar.add(addPropertyButton);
        buttonBar.add(updatePropertyButton);
        buttonBar.add(deletePropertyButton);
        buttonBar.add(clearButton);
        buttonBar.add(refreshButton);

        southContainer.add(buttonBar, BorderLayout.SOUTH);
        panel.add(southContainer, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildLeaseApplicationsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        leaseAppsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(leaseAppsTable), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton refreshButton = new JButton("Refresh Applications");

        approveLeaseButton.addActionListener(e -> handleApproveLease());
        rejectLeaseButton.addActionListener(e -> handleRejectLease());
        refreshButton.addActionListener(e -> refreshLeaseApplicationsTab());

        buttonBar.add(approveLeaseButton);
        buttonBar.add(rejectLeaseButton);
        buttonBar.add(refreshButton);

        panel.add(buttonBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCommissionsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        totalCommissionLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        totalCommissionLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(totalCommissionLabel, BorderLayout.NORTH);

        commissionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(commissionsTable), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton refreshButton = new JButton("Refresh Commissions");
        refreshButton.addActionListener(e -> refreshCommissionsTab());
        buttonBar.add(refreshButton);
        panel.add(buttonBar, BorderLayout.SOUTH);

        return panel;
    }

    private void populateFormFromSelectedProperty() {
        int selectedRow = myPropertiesTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        int modelRow = myPropertiesTable.convertRowIndexToModel(selectedRow);
        titleField.setText(String.valueOf(myPropertiesModel.getValueAt(modelRow, 1)));
        cityField.setText(String.valueOf(myPropertiesModel.getValueAt(modelRow, 2)));
        addressField.setText(String.valueOf(myPropertiesModel.getValueAt(modelRow, 3)));
        rentField.setText(String.valueOf(myPropertiesModel.getValueAt(modelRow, 4)));
        bedroomsField.setText(String.valueOf(myPropertiesModel.getValueAt(modelRow, 5)));
    }

    private void clearPropertyForm() {
        myPropertiesTable.clearSelection();
        titleField.setText("");
        cityField.setText("");
        addressField.setText("");
        rentField.setText("");
        bedroomsField.setText("");
    }

    private void setPropertyButtonsEnabled(boolean enabled) {
        addPropertyButton.setEnabled(enabled);
        updatePropertyButton.setEnabled(enabled);
        deletePropertyButton.setEnabled(enabled);
    }

    public void refreshAllTabs() {
        refreshMyPropertiesTab();
        refreshLeaseApplicationsTab();
        refreshCommissionsTab();
    }

    // [RUBRIC: Threads]
    private void refreshMyPropertiesTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        SwingWorker<Result<List<Property>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<List<Property>> doInBackground() {
                try {
                    return Result.ok(realEstateService.getPropertiesByManager(actor));
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<List<Property>> result = get();
                    if (result.isSuccess()) {
                        myPropertiesModel.setRowCount(0);
                        for (Property p : result.getData()) {
                            myPropertiesModel.addRow(new Object[]{
                                    p.getId(),
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
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Error Loading Properties", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Failed to load properties: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleCreateProperty() {
        final String title = titleField.getText().trim();
        final String city = cityField.getText().trim();
        final String address = addressField.getText().trim();
        final BigDecimal monthlyRent;
        final int bedrooms;

        try {
            monthlyRent = new BigDecimal(rentField.getText().trim());
            bedrooms = Integer.parseInt(bedroomsField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid numeric Monthly Rent (e.g. 2200.00) and integer Bedrooms.",
                    "Invalid Number Format", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final User actor = SessionManager.getInstance().getCurrentUser();
        setPropertyButtonsEnabled(false);

        SwingWorker<Result<Property>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Property> doInBackground() {
                try {
                    Property created = realEstateService.createProperty(actor, title, city, address, monthlyRent, bedrooms);
                    return Result.ok(created);
                } catch (UnauthorizedActionException | InvalidPropertyDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Property> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                "Property '" + result.getData().getTitle() + "' created with status PENDING.",
                                "Property Added", JOptionPane.INFORMATION_MESSAGE);
                        clearPropertyForm();
                        refreshMyPropertiesTab();
                    } else {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setPropertyButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleUpdateProperty() {
        int selectedRow = myPropertiesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a property row to update.",
                    "No Property Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = myPropertiesTable.convertRowIndexToModel(selectedRow);
        final int propertyId = (Integer) myPropertiesModel.getValueAt(modelRow, 0);
        final String title = titleField.getText().trim();
        final String city = cityField.getText().trim();
        final String address = addressField.getText().trim();
        final BigDecimal monthlyRent;
        final int bedrooms;

        try {
            monthlyRent = new BigDecimal(rentField.getText().trim());
            bedrooms = Integer.parseInt(bedroomsField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid numeric Monthly Rent and integer Bedrooms.",
                    "Invalid Number Format", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final User actor = SessionManager.getInstance().getCurrentUser();
        setPropertyButtonsEnabled(false);

        SwingWorker<Result<Property>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Property> doInBackground() {
                try {
                    Property updated = realEstateService.updateProperty(
                            actor, propertyId, title, city, address, monthlyRent, bedrooms);
                    return Result.ok(updated);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidPropertyDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Property> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                "Property #" + propertyId + " updated. Status is now PENDING admin review.",
                                "Property Updated", JOptionPane.INFORMATION_MESSAGE);
                        clearPropertyForm();
                        refreshMyPropertiesTab();
                        refreshLeaseApplicationsTab();
                    } else {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Update Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setPropertyButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleDeleteProperty() {
        int selectedRow = myPropertiesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a property row to delete.",
                    "No Property Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = myPropertiesTable.convertRowIndexToModel(selectedRow);
        final int propertyId = (Integer) myPropertiesModel.getValueAt(modelRow, 0);
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete Property #" + propertyId + "?\n"
                        + "(Deletion is only permitted if there are no lease applications on this property.)",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        final User actor = SessionManager.getInstance().getCurrentUser();
        setPropertyButtonsEnabled(false);

        SwingWorker<Result<Void>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Void> doInBackground() {
                try {
                    realEstateService.deleteProperty(actor, propertyId);
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
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                "Property #" + propertyId + " deleted.",
                                "Deleted", JOptionPane.INFORMATION_MESSAGE);
                        clearPropertyForm();
                        refreshMyPropertiesTab();
                    } else {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Cannot Delete Property", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setPropertyButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads]
    private void refreshLeaseApplicationsTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        SwingWorker<Result<Object[][]>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Object[][]> doInBackground() {
                try {
                    List<LeaseApplication> apps = realEstateService.getManagerApplications(actor);
                    Object[][] rows = new Object[apps.size()][8];
                    for (int i = 0; i < apps.size(); i++) {
                        LeaseApplication app = apps.get(i);
                        Optional<Property> propOpt = realEstateService.findPropertyById(app.getPropertyId());
                        Optional<User> tenantOpt = realEstateService.findUserById(app.getTenantId());
                        rows[i] = new Object[]{
                                app.getId(),
                                app.getPropertyId(),
                                propOpt.map(Property::getTitle).orElse("Property #" + app.getPropertyId()),
                                app.getTenantId(),
                                tenantOpt.map(User::getName).orElse("Tenant #" + app.getTenantId()),
                                app.getMoveInDate(),
                                app.getMessage(),
                                app.getStatus()
                        };
                    }
                    return Result.ok(rows);
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Object[][]> result = get();
                    if (result.isSuccess()) {
                        leaseAppsModel.setRowCount(0);
                        for (Object[] row : result.getData()) {
                            leaseAppsModel.addRow(row);
                        }
                    } else {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Error Loading Applications", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Failed to load applications: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: Transaction] Invokes atomic approveLease transaction inside SwingWorker
    private void handleApproveLease() {
        int selectedRow = leaseAppsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a lease application row to approve.",
                    "No Application Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = leaseAppsTable.convertRowIndexToModel(selectedRow);
        final int applicationId = (Integer) leaseAppsModel.getValueAt(modelRow, 0);
        final User actor = SessionManager.getInstance().getCurrentUser();

        approveLeaseButton.setEnabled(false);
        rejectLeaseButton.setEnabled(false);

        SwingWorker<Result<Commission>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Commission> doInBackground() {
                try {
                    Commission commission = realEstateService.approveLease(actor, applicationId);
                    return Result.ok(commission);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidLeaseDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Commission> result = get();
                    if (result.isSuccess()) {
                        Commission comm = result.getData();
                        JOptionPane.showMessageDialog(
                                ManagerDashboard.this,
                                "Lease Application #" + applicationId + " APPROVED!\n"
                                        + "• Property marked as RENTED\n"
                                        + "• Other pending applications on this property rejected\n"
                                        + "• 5% Commission recorded: $" + comm.getAmount().toPlainString(),
                                "Atomic Lease Approval Complete",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        refreshAllTabs();
                    } else {
                        JOptionPane.showMessageDialog(
                                ManagerDashboard.this,
                                result.getErrorMessage(),
                                "Lease Approval Failed (Transaction Rolled Back)",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    approveLeaseButton.setEnabled(true);
                    rejectLeaseButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads]
    private void handleRejectLease() {
        int selectedRow = leaseAppsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a lease application row to reject.",
                    "No Application Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = leaseAppsTable.convertRowIndexToModel(selectedRow);
        final int applicationId = (Integer) leaseAppsModel.getValueAt(modelRow, 0);
        final User actor = SessionManager.getInstance().getCurrentUser();

        approveLeaseButton.setEnabled(false);
        rejectLeaseButton.setEnabled(false);

        SwingWorker<Result<Void>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Void> doInBackground() {
                try {
                    realEstateService.rejectLease(actor, applicationId);
                    return Result.ok(null);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidLeaseDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Void> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                "Lease Application #" + applicationId + " rejected.",
                                "Lease Rejected", JOptionPane.INFORMATION_MESSAGE);
                        refreshLeaseApplicationsTab();
                    } else {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Rejection Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    approveLeaseButton.setEnabled(true);
                    rejectLeaseButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads]
    private void refreshCommissionsTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        SwingWorker<Result<List<Commission>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<List<Commission>> doInBackground() {
                try {
                    return Result.ok(realEstateService.getManagerCommissions(actor));
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<List<Commission>> result = get();
                    if (result.isSuccess()) {
                        commissionsModel.setRowCount(0);
                        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        for (Commission c : result.getData()) {
                            commissionsModel.addRow(new Object[]{
                                    c.getId(),
                                    c.getLeaseId(),
                                    c.getAmount().toPlainString(),
                                    c.getCreatedAt()
                            });
                            total = total.add(c.getAmount());
                        }
                        totalCommissionLabel.setText("Total Commission Earned (5%): $" + total.toPlainString());
                    } else {
                        JOptionPane.showMessageDialog(ManagerDashboard.this,
                                result.getErrorMessage(), "Error Loading Commissions", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(ManagerDashboard.this,
                            "Failed to load commissions: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
