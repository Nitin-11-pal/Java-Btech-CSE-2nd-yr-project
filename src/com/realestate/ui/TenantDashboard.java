package com.realestate.ui;

import com.realestate.exception.InvalidLeaseDataException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedActionException;
import com.realestate.model.LeaseApplication;
import com.realestate.model.Property;
import com.realestate.model.User;
import com.realestate.service.RealEstateService;
import com.realestate.util.Result;
import com.realestate.util.SessionManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Tenant Swing dashboard with tabs for searching/filtering APPROVED properties (with Set<String>
 * city filter and Comparator sorting) and submitting/tracking Lease Applications.
 */
// [RUBRIC: OOP] [RUBRIC: Collections] [RUBRIC: Threads]
public class TenantDashboard extends JPanel {

    private final MainFrame mainFrame;
    private final RealEstateService realEstateService;

    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Tab 1: Search & Filter Controls
    private final JComboBox<String> cityFilterCombo = new JComboBox<>(new String[]{"ALL"});
    private final JTextField maxRentField = new JTextField(10);
    private final JTextField minBedroomsField = new JTextField(6);
    private final JComboBox<String> sortOrderCombo = new JComboBox<>(
            new String[]{"Rent: Low to High", "Rent: High to Low"}
    );

    private final DefaultTableModel searchResultsModel = new DefaultTableModel(
            new Object[]{"Property ID", "Title", "City", "Address", "Monthly Rent ($)", "Bedrooms", "Status"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable searchResultsTable = new JTable(searchResultsModel);

    // Lease Application submission controls
    private final JTextField moveInDateField = new JTextField(LocalDate.now().plusDays(14).toString(), 10);
    private final JTextField applicationMessageField = new JTextField(32);
    private final JButton applyButton = new JButton("Submit Lease Application");

    // Tab 2: My Lease Applications
    private final DefaultTableModel myApplicationsModel = new DefaultTableModel(
            new Object[]{"App ID", "Property ID", "Property Title", "City", "Monthly Rent ($)", "Move-In Date", "Message", "Status"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable myApplicationsTable = new JTable(myApplicationsModel);

    public TenantDashboard(MainFrame mainFrame, RealEstateService realEstateService) {
        this.mainFrame = mainFrame;
        this.realEstateService = realEstateService;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tabbedPane.addTab("Search Approved Properties", buildSearchTab());
        tabbedPane.addTab("My Lease Applications", buildMyApplicationsTab());

        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx == 0) {
                refreshCityDropdownAndSearch();
            } else if (idx == 1) {
                refreshMyApplicationsTab();
            }
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel buildSearchTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        // Top Filter Bar
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filter & Sort Approved Listings (All filters optional)"));

        filterPanel.add(new JLabel("City:"));
        filterPanel.add(cityFilterCombo);

        filterPanel.add(new JLabel("Max Budget ($):"));
        filterPanel.add(maxRentField);

        filterPanel.add(new JLabel("Min Bedrooms:"));
        filterPanel.add(minBedroomsField);

        filterPanel.add(new JLabel("Sort:"));
        filterPanel.add(sortOrderCombo);

        JButton searchButton = new JButton("Search");
        JButton resetButton = new JButton("Reset");

        searchButton.addActionListener(e -> executePropertySearch());
        resetButton.addActionListener(e -> {
            maxRentField.setText("");
            minBedroomsField.setText("");
            sortOrderCombo.setSelectedIndex(0);
            cityFilterCombo.setSelectedItem("ALL");
            refreshCityDropdownAndSearch();
        });

        filterPanel.add(searchButton);
        filterPanel.add(resetButton);
        panel.add(filterPanel, BorderLayout.NORTH);

        // Center Table
        searchResultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(searchResultsTable), BorderLayout.CENTER);

        // Bottom Lease Application Form
        JPanel applyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        applyPanel.setBorder(BorderFactory.createTitledBorder("Submit Lease Application for Selected Property"));

        applyPanel.add(new JLabel("Move-In Date (YYYY-MM-DD):"));
        applyPanel.add(moveInDateField);

        applyPanel.add(new JLabel("Message:"));
        applyPanel.add(applicationMessageField);

        applyButton.addActionListener(e -> handleSubmitLeaseApplication());
        applyPanel.add(applyButton);

        panel.add(applyPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMyApplicationsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        myApplicationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(myApplicationsTable), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton refreshButton = new JButton("Refresh My Applications");
        refreshButton.addActionListener(e -> refreshMyApplicationsTab());
        buttonBar.add(refreshButton);

        panel.add(buttonBar, BorderLayout.SOUTH);
        return panel;
    }

    public void refreshAllTabs() {
        refreshCityDropdownAndSearch();
        refreshMyApplicationsTab();
    }

    // [RUBRIC: Collections] [RUBRIC: Threads] Loads Set<String> of cities in background and runs search
    private void refreshCityDropdownAndSearch() {
        SwingWorker<Result<Set<String>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Set<String>> doInBackground() {
                try {
                    return Result.ok(realEstateService.getApprovedCities());
                } catch (SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<Set<String>> result = get();
                    if (result.isSuccess()) {
                        String currentSelection = (String) cityFilterCombo.getSelectedItem();
                        cityFilterCombo.removeAllItems();
                        cityFilterCombo.addItem("ALL");
                        String matchedSelection = "ALL";
                        for (String city : result.getData()) {
                            cityFilterCombo.addItem(city);
                            if (currentSelection != null && city.equalsIgnoreCase(currentSelection)) {
                                matchedSelection = city;
                            }
                        }
                        cityFilterCombo.setSelectedItem(matchedSelection);
                    }
                } catch (InterruptedException | ExecutionException ignored) {
                } finally {
                    executePropertySearch();
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Collections] [RUBRIC: Threads] Searches ArrayList<Property> via SwingWorker
    private void executePropertySearch() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        final String selectedCity = (String) cityFilterCombo.getSelectedItem();
        final String maxRentRaw = maxRentField.getText().trim();
        final String minBedsRaw = minBedroomsField.getText().trim();
        final boolean sortAscending = sortOrderCombo.getSelectedIndex() == 0;

        final BigDecimal maxRent;
        if (!maxRentRaw.isEmpty()) {
            try {
                maxRent = new BigDecimal(maxRentRaw);
                if (maxRent.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Max Budget must be greater than 0.",
                            "Invalid Filter", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Max Budget must be a valid number (e.g. 2500 or 2500.00).",
                        "Invalid Filter", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            maxRent = null;
        }

        final Integer minBedrooms;
        if (!minBedsRaw.isEmpty()) {
            try {
                minBedrooms = Integer.parseInt(minBedsRaw);
                if (minBedrooms < 0) {
                    JOptionPane.showMessageDialog(this,
                            "Min Bedrooms must be 0 or greater.",
                            "Invalid Filter", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Min Bedrooms must be a valid integer.",
                        "Invalid Filter", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            minBedrooms = null;
        }

        SwingWorker<Result<ArrayList<Property>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<ArrayList<Property>> doInBackground() {
                try {
                    ArrayList<Property> matches = realEstateService.searchApprovedProperties(
                            actor, selectedCity, maxRent, minBedrooms, sortAscending);
                    return Result.ok(matches);
                } catch (UnauthorizedActionException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<ArrayList<Property>> result = get();
                    if (result.isSuccess()) {
                        searchResultsModel.setRowCount(0);
                        for (Property p : result.getData()) {
                            searchResultsModel.addRow(new Object[]{
                                    p.getId(),
                                    p.getTitle(),
                                    p.getCity(),
                                    p.getAddress(),
                                    p.getMonthlyRent().toPlainString(),
                                    p.getBedrooms(),
                                    p.getStatus()
                            });
                        }
                    } else {
                        JOptionPane.showMessageDialog(TenantDashboard.this,
                                result.getErrorMessage(), "Search Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(TenantDashboard.this,
                            "Unexpected error during search: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads] [RUBRIC: OOP]
    private void handleSubmitLeaseApplication() {
        int selectedRow = searchResultsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select an approved property from the table before applying.",
                    "No Property Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = searchResultsTable.convertRowIndexToModel(selectedRow);
        final int propertyId = (Integer) searchResultsModel.getValueAt(modelRow, 0);
        final String dateText = moveInDateField.getText().trim();
        final String message = applicationMessageField.getText().trim();

        final LocalDate moveInDate;
        try {
            moveInDate = LocalDate.parse(dateText);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Move-in date must be in YYYY-MM-DD format (e.g. " + LocalDate.now().plusDays(14) + ").",
                    "Invalid Date Format", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final User actor = SessionManager.getInstance().getCurrentUser();
        applyButton.setEnabled(false);

        SwingWorker<Result<LeaseApplication>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<LeaseApplication> doInBackground() {
                try {
                    LeaseApplication created = realEstateService.submitLeaseApplication(
                            actor, propertyId, moveInDate, message);
                    return Result.ok(created);
                } catch (UnauthorizedActionException | ResourceNotFoundException | InvalidLeaseDataException | SQLException e) {
                    return Result.fail(e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<LeaseApplication> result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(TenantDashboard.this,
                                "Lease application #" + result.getData().getId()
                                        + " submitted with status PENDING!",
                                "Application Submitted", JOptionPane.INFORMATION_MESSAGE);
                        applicationMessageField.setText("");
                        refreshMyApplicationsTab();
                    } else {
                        JOptionPane.showMessageDialog(TenantDashboard.this,
                                result.getErrorMessage(), "Lease Application Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(TenantDashboard.this,
                            "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    applyButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // [RUBRIC: Threads]
    private void refreshMyApplicationsTab() {
        final User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            mainFrame.showLoginPanel();
            return;
        }

        SwingWorker<Result<Object[][]>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<Object[][]> doInBackground() {
                try {
                    List<LeaseApplication> apps = realEstateService.getTenantApplications(actor);
                    Object[][] rows = new Object[apps.size()][8];
                    for (int i = 0; i < apps.size(); i++) {
                        LeaseApplication app = apps.get(i);
                        Optional<Property> propOpt = realEstateService.findPropertyById(app.getPropertyId());
                        rows[i] = new Object[]{
                                app.getId(),
                                app.getPropertyId(),
                                propOpt.map(Property::getTitle).orElse("Property #" + app.getPropertyId()),
                                propOpt.map(Property::getCity).orElse("-"),
                                propOpt.map(p -> p.getMonthlyRent().toPlainString()).orElse("-"),
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
                        myApplicationsModel.setRowCount(0);
                        for (Object[] row : result.getData()) {
                            myApplicationsModel.addRow(row);
                        }
                    } else {
                        JOptionPane.showMessageDialog(TenantDashboard.this,
                                result.getErrorMessage(), "Error Loading Applications", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(TenantDashboard.this,
                            "Failed to load applications: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
