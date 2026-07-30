package org.example.ui;

import org.example.model.auth.exceptions.*;
import org.example.service.auth.AuthService;
import org.example.util.UIUtils;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class RegisterJFrame extends JFrame {
    private final LoginJFrame loginFrame;
    private final AuthService authService;

    private JTextField userInputField;
    private JTextField emailInputField;
    private JPasswordField passwordInputField;
    private JPasswordField confirmPasswordField;
    private JButton registerButton;
    private JButton cancelButton;

    public RegisterJFrame(LoginJFrame loginFrame, AuthService authService) {
        this.loginFrame = loginFrame;
        this.authService = authService;

        initComponents();
    }

    /**
     * Initializes window properties, form fields, layout bounds, and keyboard action mappings.
     */
    private void initComponents() {
        this.setUndecorated(true);
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleCancel();
            }
        });

        this.setLayout(null);
        this.setSize(310, 210);
        this.getRootPane().setBorder(BorderFactory.createEtchedBorder());

        // Username
        JLabel userLabel = new JLabel("Username: ");
        userLabel.setBounds(20, 20, 110, 20);
        this.add(userLabel);

        userInputField = new JTextField();
        userInputField.setBounds(130, 20, 150, 20);
        this.add(userInputField);

        // Email
        JLabel emailLabel = new JLabel("Email: ");
        emailLabel.setBounds(20, 55, 110, 20);
        this.add(emailLabel);

        emailInputField = new JTextField();
        emailInputField.setBounds(130, 55, 150, 20);
        this.add(emailInputField);

        // Password
        JLabel passwordLabel = new JLabel("Password: ");
        passwordLabel.setBounds(20, 90, 110, 20);
        this.add(passwordLabel);

        passwordInputField = new JPasswordField();
        passwordInputField.setBounds(130, 90, 150, 20);
        this.add(passwordInputField);

        // Confirm Password
        JLabel confirmLabel = new JLabel("Confirm Password: ");
        confirmLabel.setBounds(20, 125, 110, 20);
        this.add(confirmLabel);

        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setBounds(130, 125, 150, 20);
        this.add(confirmPasswordField);

        // Buttons
        registerButton = new JButton("Register");
        registerButton.setBounds(20, 165, 125, 25);
        this.add(registerButton);

        cancelButton = new JButton("Cancel");
        cancelButton.setBounds(155, 165, 125, 25);
        this.add(cancelButton);

        // --- KEYBOARD SHORTCUTS ---
        this.getRootPane().setDefaultButton(registerButton);
        this.getRootPane().registerKeyboardAction(
                e -> handleCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Action Listeners
        registerButton.addActionListener(e -> handleRegister());
        cancelButton.addActionListener(e -> handleCancel());

        this.setLocationRelativeTo(null);
    }

    /**
     * Validates input fields and performs user registration asynchronously using SwingWorker.
     */
    private void handleRegister() {
        String username = userInputField.getText().trim();
        String email = emailInputField.getText().trim();
        String password = new String(passwordInputField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        setUIEnabled(false);

        new SwingWorker<Void, Void>() {
            private Exception exception = null;

            @Override
            protected Void doInBackground() {
                try {
                    authService.register(username, password, confirmPassword, email);
                } catch (Exception e) {
                    this.exception = e;
                }
                return null;
            }

            @Override
            protected void done() {
                setUIEnabled(true);

                if (exception != null) {
                    if (isValidationException(exception) || exception.getMessage().equals("Passwords do not match!")) {
                        UIUtils.showMessageDialog(RegisterJFrame.this, exception.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
                    } else {
                        UIUtils.showMessageDialog(RegisterJFrame.this, "System error: " + exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    resetPasswordFields();
                    return;
                }

                UIUtils.showMessageDialog(
                        RegisterJFrame.this,
                        "Account created successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE
                );

                returnToLogin(username);
            }
        }.execute();
    }

    /**
     * Checks if an exception originates from account credential validation errors.
     */
    private boolean isValidationException(Exception ex) {
        return ex instanceof InvalidUsernameException
                || ex instanceof InvalidPasswordException
                || ex instanceof InvalidLengthException
                || ex instanceof InvalidEmailException
                || ex instanceof NumberException
                || ex instanceof SpecialCharacterException;
    }

    /**
     * Clears text and password fields in the registration form.
     */
    public void clearFields() {
        userInputField.setText("");
        emailInputField.setText("");
        passwordInputField.setText("");
        confirmPasswordField.setText("");
    }

    /**
     * Clears password input fields and requests focus back to the primary password field.
     */
    private void resetPasswordFields() {
        passwordInputField.setText("");
        confirmPasswordField.setText("");
        passwordInputField.requestFocusInWindow();
    }

    /**
     * Enables or disables registration action buttons during background worker execution.
     */
    private void setUIEnabled(boolean enabled) {
        registerButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
    }

    /**
     * Prompts the user with a confirmation dialog before cancelling registration.
     */
    private void handleCancel() {
        boolean confirm = UIUtils.showConfirmDialog(
                this,
                "Cancel registration?",
                "Cancel"
        );

        if (confirm) {
            returnToLogin();
        }
    }

    /**
     * Returns to the login window, optionally prefilling the registered username.
     */
    private void returnToLogin(String registeredUsername) {
        this.setVisible(false);

        if (loginFrame != null) {
            if (registeredUsername != null) {
                loginFrame.prefillUsername(registeredUsername);
            }
            loginFrame.setLocationRelativeTo(null);
            loginFrame.setVisible(true);
        }
    }

    /**
     * Returns to the login window without prefilling any username.
     */
    private void returnToLogin() {
        returnToLogin(null);
    }
}