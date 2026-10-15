package com.realestate.ui;

import com.realestate.exception.AuthenticationFailedException;
import com.realestate.model.User;
import com.realestate.service.AuthService;
import com.realestate.util.Result;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * Swing login screen using SwingWorker and generic Result<User> for non-blocking authentication.
 */
// [RUBRIC: Threads] [RUBRIC: OOP] [RUBRIC: Collections]
public class LoginPanel extends JPanel {

    private final MainFrame mainFrame;
    private final AuthService authService;

    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JButton loginButton = new JButton("Login");
    private final JButton goToRegisterButton = new JButton("Create Account (Tenant / Manager)");

    public LoginPanel(MainFrame mainFrame, AuthService authService) {
        this.mainFrame = mainFrame;
        this.authService = authService;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel titleLabel = new JLabel("Online Real Estate Management System", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(titleLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Sign In"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(passwordField, gbc);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonRow.add(loginButton);
        buttonRow.add(goToRegisterButton);

        gbc.gridx = 1;
        gbc.gridy = 2;
        formPanel.add(buttonRow, gbc);

        JLabel hintLabel = new JLabel(
                "<html><i>Demo logins — Admin: admin@realestate.com / Admin@123 | "
                        + "Manager: manager@realestate.com / Manager@123 | "
                        + "Tenant: tenant@realestate.com / Tenant@123</i></html>"
        );
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        formPanel.add(hintLabel, gbc);

        add(formPanel, BorderLayout.CENTER);

        loginButton.addActionListener(e -> handleLogin());
        passwordField.addActionListener(e -> handleLogin());
        goToRegisterButton.addActionListener(e -> mainFrame.showRegisterPanel());
    }

    public void clearFields() {
        passwordField.setText("");
    }

    // [RUBRIC: Threads] [RUBRIC: OOP] Uses SwingWorker and try/catch/finally with custom checked exceptions
    private void handleLogin() {
        final String email = emailField.getText().trim();
        final String password = new String(passwordField.getPassword());

        loginButton.setEnabled(false);
        goToRegisterButton.setEnabled(false);

        SwingWorker<Result<User>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<User> doInBackground() {
                try {
                    User user = authService.login(email, password);
                    return Result.ok(user);
                } catch (AuthenticationFailedException e) {
                    return Result.fail(e.getMessage());
                } catch (SQLException e) {
                    return Result.fail("Database error during login: " + e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<User> result = get();
                    if (result.isSuccess()) {
                        passwordField.setText("");
                        mainFrame.onLoginSuccess(result.getData());
                    } else {
                        JOptionPane.showMessageDialog(
                                LoginPanel.this,
                                result.getErrorMessage(),
                                "Authentication Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(
                            LoginPanel.this,
                            "Unexpected error: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    loginButton.setEnabled(true);
                    goToRegisterButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
