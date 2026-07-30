package org.example.ui;

import org.example.infrastructure.inventory.MySQLBlueprintRepository;
import org.example.infrastructure.inventory.MySQLInventoryRepository;
import org.example.infrastructure.inventory.alarms.BlueprintThresholdWatcher;
import org.example.infrastructure.inventory.alarms.GlobalTargetWatcher;
import org.example.infrastructure.mission.MySQLMissionRepository;
import org.example.model.auth.exceptions.*;
import org.example.service.auth.AuthService;
import org.example.service.inventory.*;
import org.example.service.mission.MissionRepository;
import org.example.service.mission.MissionService;
import org.example.util.UIUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Optional;

public class LoginJFrame extends JFrame {
    private final AuthService authService;
    private RegisterJFrame registerFrame;

    private JTextField userInputField;
    private JPasswordField passwordInputField;
    private JButton loginButton;
    private JButton registerButton;
    private JButton closeButton;

    public LoginJFrame(AuthService authService) {
        this.authService = authService;
        initComponents();
    }

    /**
     * Initializes components, layout bounds, and key bindings for the login screen.
     */
    private void initComponents() {
        this.setUndecorated(true);
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.setLayout(null);

        this.setSize(310, 190);
        this.setLocationRelativeTo(null);
        this.getRootPane().setBorder(BorderFactory.createEtchedBorder());

        JLabel userLabel = new JLabel("Username/Email: ");
        userLabel.setBounds(20, 20, 90, 20);
        this.add(userLabel);

        userInputField = new JTextField();
        userInputField.setBounds(120, 20, 160, 20);
        this.add(userInputField);

        JLabel passwordLabel = new JLabel("Password: ");
        passwordLabel.setBounds(20, 55, 90, 20);
        this.add(passwordLabel);

        passwordInputField = new JPasswordField();
        passwordInputField.setBounds(120, 55, 160, 20);
        this.add(passwordInputField);

        loginButton = new JButton("Login");
        loginButton.setBounds(20, 95, 125, 25);
        this.add(loginButton);

        registerButton = new JButton("Register");
        registerButton.setBounds(155, 95, 125, 25);
        this.add(registerButton);

        closeButton = new JButton("Close");
        closeButton.setBounds(20, 135, 260, 25);
        this.add(closeButton);

        this.getRootPane().setDefaultButton(loginButton);
        this.getRootPane().registerKeyboardAction(
                e -> handleClose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        loginButton.addActionListener(e -> handleLogin());
        registerButton.addActionListener(e -> openRegisterWindow());
        closeButton.addActionListener(e -> handleClose());

        this.setVisible(true);
    }

    /**
     * Hides the login window and opens the user registration form.
     */
    private void openRegisterWindow() {
        this.setVisible(false);

        if (registerFrame == null) {
            registerFrame = new RegisterJFrame(this, authService);
        } else {
            registerFrame.clearFields();
            registerFrame.setLocationRelativeTo(null);
        }

        registerFrame.setVisible(true);
    }

    /**
     * Executes the login authentication process asynchronously using SwingWorker.
     */
    private void handleLogin() {
        String username = userInputField.getText().trim();
        String password = new String(passwordInputField.getPassword());

        setUIEnabled(false);

        new SwingWorker<Optional<Long>, Void>() {
            private Exception exception;

            @Override
            protected Optional<Long> doInBackground() {
                try {
                    return authService.login(username, password);
                } catch (Exception e) {
                    this.exception = e;
                    return Optional.empty();
                }
            }

            @Override
            protected void done() {
                setUIEnabled(true);

                if (exception != null) {
                    int msgType = isValidationException(exception) ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE;
                    String title = isValidationException(exception) ? "Validation Error" : "Login Error";

                    UIUtils.showMessageDialog(LoginJFrame.this, exception.getMessage(), title, msgType);
                    resetPassword();
                    return;
                }

                try {
                    Optional<Long> userIdOpt = get();
                    if (userIdOpt.isPresent()) {
                        long userId = userIdOpt.get();
                        instantiateMainframe(userId);
                    } else {
                        UIUtils.showMessageDialog(LoginJFrame.this, "Invalid credentials.", "Login Error", JOptionPane.ERROR_MESSAGE);
                        resetPassword();
                    }
                } catch (Exception e) {
                    UIUtils.showMessageDialog(LoginJFrame.this, "Error initializing session: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Checks if an exception stems from input validation failures.
     */
    private boolean isValidationException(Exception ex) {
        return ex instanceof InvalidUsernameException || ex instanceof InvalidPasswordException
                || ex instanceof InvalidLengthException || ex instanceof InvalidEmailException
                || ex instanceof NumberException || ex instanceof SpecialCharacterException;
    }

    /**
     * Instantiates repositories, services, observers, and transitions to the MainFrame upon successful login.
     */
    private void instantiateMainframe(long userId) {
        if (registerFrame != null) {
            registerFrame.dispose();
        }

        InventoryRepository inventoryRepository = new MySQLInventoryRepository(userId);
        BlueprintRepository blueprintRepository = new MySQLBlueprintRepository(userId);
        MissionRepository missionRepository = new MySQLMissionRepository(userId);

        InventoryService userInventoryService = new InventoryService(inventoryRepository);

        // --- Register Alarms ---
        InventoryObserver globalWatcher = new GlobalTargetWatcher(blueprintRepository,
                msg -> UIUtils.showToastBottomRight(null,"Global Target Met!", msg, new Color(52, 152, 219)));

        InventoryObserver blueprintWatcher = new BlueprintThresholdWatcher(blueprintRepository, inventoryRepository,
                msg -> UIUtils.showToastBottomRight(null,"Crafting Ready!", msg, new Color(46, 204, 113)));

        userInventoryService.registerObserver(globalWatcher);
        userInventoryService.registerObserver(blueprintWatcher);

        FoundryService userFoundryService = new FoundryService(userInventoryService, blueprintRepository);
        MissionService userMissionService = new MissionService(userInventoryService, missionRepository);

        resetPassword();

        LoginJFrame.this.dispose();
        new MainFrame(authService, userInventoryService, userFoundryService, userMissionService, userId).setVisible(true);
    }

    /**
     * Clears the password input field and shifts focus back to it.
     */
    private void resetPassword() {
        passwordInputField.setText("");
        passwordInputField.requestFocusInWindow();
    }

    /**
     * Enables or disables action buttons during background worker execution.
     */
    private void setUIEnabled(boolean enabled) {
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
        closeButton.setEnabled(enabled);
    }

    /**
     * Prompts the user with a confirmation dialog before terminating the application.
     */
    private void handleClose() {
        boolean confirm = UIUtils.showConfirmDialog(this,
                "Are you sure you want to close the application?",
                "Exit");
        if (confirm) {
            System.exit(0);
        }
    }

    /**
     * Prefills the username field from external sources (such as registration flow).
     */
    public void prefillUsername(String username) {
        if (userInputField != null && username != null && !username.trim().isEmpty()) {
            userInputField.setText(username.trim());
            passwordInputField.requestFocusInWindow();
        }
    }
}