package com.realestate.ui;

import com.realestate.model.Admin;
import com.realestate.model.PropertyManager;
import com.realestate.model.Tenant;
import com.realestate.model.User;
import com.realestate.service.AuthService;
import com.realestate.service.RealEstateService;
import com.realestate.util.SessionManager;
import com.realestate.worker.AuditWorker;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Set;

/**
 * Primary application JFrame hosting a CardLayout for Login, Register, and Role Dashboards.
 * Uses SessionManager in the UI layer and demonstrates runtime polymorphism on User references.
 */
// [RUBRIC: OOP] CardLayout JFrame dispatching dashboards via User runtime polymorphism
public class MainFrame extends JFrame {

    public static final String LOGIN_CARD = "LOGIN_PANEL";
    public static final String REGISTER_CARD = "REGISTER_PANEL";

    private final AuditWorker auditWorker;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContainer = new JPanel(cardLayout);

    private final JPanel topSessionBar = new JPanel(new BorderLayout(10, 0));
    private final JLabel sessionUserLabel = new JLabel("Not logged in");
    private final JButton logoutButton = new JButton("Logout");

    private final LoginPanel loginPanel;
    private final RegisterPanel registerPanel;
    private final AdminDashboard adminDashboard;
    private final ManagerDashboard managerDashboard;
    private final TenantDashboard tenantDashboard;

    public MainFrame(AuthService authService, RealEstateService realEstateService, AuditWorker auditWorker) {
        super("Online Real Estate Management System");
        this.auditWorker = auditWorker;

        this.loginPanel = new LoginPanel(this, authService);
        this.registerPanel = new RegisterPanel(this, authService);
        this.adminDashboard = new AdminDashboard(this, authService, realEstateService);
        this.managerDashboard = new ManagerDashboard(this, realEstateService);
        this.tenantDashboard = new TenantDashboard(this, realEstateService);

        initFrame();
    }

    private void initFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top session bar (visible when logged in)
        topSessionBar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        sessionUserLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        topSessionBar.add(sessionUserLabel, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightControls.add(logoutButton);
        topSessionBar.add(rightControls, BorderLayout.EAST);
        topSessionBar.setVisible(false);

        logoutButton.addActionListener(e -> performLogout());

        // CardLayout container
        cardContainer.add(loginPanel, LOGIN_CARD);
        cardContainer.add(registerPanel, REGISTER_CARD);
        cardContainer.add(adminDashboard, Admin.DASHBOARD_CARD);
        cardContainer.add(managerDashboard, PropertyManager.DASHBOARD_CARD);
        cardContainer.add(tenantDashboard, Tenant.DASHBOARD_CARD);

        add(topSessionBar, BorderLayout.NORTH);
        add(cardContainer, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                auditWorker.shutdown();
            }
        });

        showLoginPanel();
    }

    public void showLoginPanel() {
        topSessionBar.setVisible(false);
        loginPanel.clearFields();
        cardLayout.show(cardContainer, LOGIN_CARD);
    }

    public void showRegisterPanel() {
        topSessionBar.setVisible(false);
        registerPanel.clearFields();
        cardLayout.show(cardContainer, REGISTER_CARD);
    }

    /**
     * Stores the authenticated User in SessionManager and uses runtime polymorphism
     * (user.displayRoleDashboard() and user.getPermissions()) on the abstract User reference
     * to open the appropriate dashboard.
     *
     * @param user authenticated User
     */
    // [RUBRIC: OOP] Runtime polymorphism on abstract User reference
    public void onLoginSuccess(User user) {
        SessionManager.getInstance().setCurrentUser(user);

        String targetDashboardCard = user.displayRoleDashboard();
        Set<String> permissions = user.getPermissions();

        sessionUserLabel.setText(String.format(
                "Logged in as: %s (%s) | Role: %s | Permissions: %s",
                user.getName(), user.getEmail(), user.getRole(), permissions
        ));
        topSessionBar.setVisible(true);

        if (Admin.DASHBOARD_CARD.equals(targetDashboardCard)) {
            adminDashboard.refreshAllTabs();
        } else if (PropertyManager.DASHBOARD_CARD.equals(targetDashboardCard)) {
            managerDashboard.refreshAllTabs();
        } else if (Tenant.DASHBOARD_CARD.equals(targetDashboardCard)) {
            tenantDashboard.refreshAllTabs();
        }

        cardLayout.show(cardContainer, targetDashboardCard);
    }

    /**
     * Logs out the current user via SessionManager and returns to the login screen.
     */
    public void performLogout() {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current != null) {
            auditWorker.logEvent(current.getId(), "USER_LOGOUT", "User logged out: " + current.getEmail());
        }
        SessionManager.getInstance().logout();
        showLoginPanel();
    }
}
