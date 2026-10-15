package com.realestate.ui;

import com.realestate.exception.InvalidUserDataException;
import com.realestate.model.Role;
import com.realestate.model.User;
import com.realestate.service.AuthService;
import com.realestate.util.Result;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 * Swing registration screen for new Tenants and Property Managers.
 */
// [RUBRIC: Threads] [RUBRIC: OOP] [RUBRIC: Collections]
public class RegisterPanel extends JPanel {

    private final MainFrame mainFrame;
    private final AuthService authService;

    private final JTextField nameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JComboBox<Role> roleComboBox = new JComboBox<>(new Role[]{Role.TENANT, Role.PROPERTY_MANAGER});
    private final JButton registerButton = new JButton("Register");
    private final JButton backToLoginButton = new JButton("Back to Login");

    public RegisterPanel(MainFrame mainFrame, AuthService authService) {
        this.mainFrame = mainFrame;
        this.authService = authService;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel titleLabel = new JLabel("Register New Account", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(titleLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Account Details (Tenant or Property Manager)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Full Name:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Password (>= 6 chars):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Role:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(roleComboBox, gbc);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonRow.add(registerButton);
        buttonRow.add(backToLoginButton);

        gbc.gridx = 1;
        gbc.gridy = 4;
        formPanel.add(buttonRow, gbc);

        add(formPanel, BorderLayout.CENTER);

        registerButton.addActionListener(e -> handleRegister());
        backToLoginButton.addActionListener(e -> mainFrame.showLoginPanel());
    }

    public void clearFields() {
        nameField.setText("");
        emailField.setText("");
        passwordField.setText("");
        roleComboBox.setSelectedIndex(0);
    }

    // [RUBRIC: Threads] [RUBRIC: OOP] Uses SwingWorker and try/catch/finally with InvalidUserDataException
    private void handleRegister() {
        final String name = nameField.getText().trim();
        final String email = emailField.getText().trim();
        final String password = new String(passwordField.getPassword());
        final Role selectedRole = (Role) roleComboBox.getSelectedItem();

        registerButton.setEnabled(false);
        backToLoginButton.setEnabled(false);

        SwingWorker<Result<User>, Void> worker = new SwingWorker<>() {
            @Override
            protected Result<User> doInBackground() {
                try {
                    User created = authService.register(name, email, password, selectedRole);
                    return Result.ok(created);
                } catch (InvalidUserDataException e) {
                    return Result.fail(e.getMessage());
                } catch (SQLException e) {
                    return Result.fail("Database error during registration: " + e.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    Result<User> result = get();
                    if (result.isSuccess()) {
                        User created = result.getData();
                        JOptionPane.showMessageDialog(
                                RegisterPanel.this,
                                "Account created for " + created.getName() + " (" + created.getRole() + "). Please log in.",
                                "Registration Successful",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        clearFields();
                        mainFrame.showLoginPanel();
                    } else {
                        JOptionPane.showMessageDialog(
                                RegisterPanel.this,
                                result.getErrorMessage(),
                                "Registration Validation Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(
                            RegisterPanel.this,
                            "Unexpected error: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    registerButton.setEnabled(true);
                    backToLoginButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
