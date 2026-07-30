package org.example.ui;

import lombok.Getter;
import org.example.service.auth.AuthService;
import org.example.service.inventory.FoundryService;
import org.example.service.inventory.InventoryService;
import org.example.service.mission.MissionService;
import org.example.util.UIUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

@Getter
public class MainFrame extends JFrame {

    private final AuthService authService;
    private final InventoryService inventoryService;
    private final FoundryService foundryService;
    private final MissionService missionService;
    private final long userId;

    private MissionsJPanel missionsJPanel;
    private InventoryJPanel inventoryJPanel;
    private FoundryJPanel foundryJPanel;
    private AnalyticsJPanel analyticsJPanel;

    public MainFrame(AuthService authService, InventoryService inventoryService, FoundryService foundryService, MissionService missionService, long userId) {
        this.authService = authService;
        this.inventoryService = inventoryService;
        this.foundryService = foundryService;
        this.missionService = missionService;
        this.userId = userId;

        initComponents();
    }

    /**
     * Initializes core frame properties, container layout, top status bar, and tab change listeners.
     */
    private void initComponents() {
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                new EmptyBorder(10, 15, 15, 15)
        ));
        setContentPane(mainPanel);

        // Top Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(245, 245, 245));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel userLabel = new JLabel("Tenno ID: #" + userId);
        userLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        topBar.add(userLabel, BorderLayout.WEST);

        JButton settingsButton = new JButton("Settings ⚙");
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> showSettingsMenu(settingsButton));
        topBar.add(settingsButton, BorderLayout.EAST);

        mainPanel.add(topBar, BorderLayout.NORTH);

        // Tabbed Pane
        missionsJPanel = new MissionsJPanel(this);
        inventoryJPanel = new InventoryJPanel(this);
        foundryJPanel = new FoundryJPanel(this);
        analyticsJPanel = new AnalyticsJPanel(this);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));

        tabs.addTab("Missions", missionsJPanel);
        tabs.addTab("Inventory", inventoryJPanel);
        tabs.addTab("Foundry", foundryJPanel);
        tabs.addTab("Analytics & Goals", analyticsJPanel);

        tabs.addChangeListener(e -> {
            switch (tabs.getSelectedIndex()) {
                case 0 -> missionsJPanel.loadMissionsAsync();
                case 1 -> inventoryJPanel.refreshData();
                case 2 -> foundryJPanel.loadDataFromService();
                case 3 -> analyticsJPanel.refreshData();
            }
        });

        mainPanel.add(tabs, BorderLayout.CENTER);
    }

    /**
     * Displays the settings popup menu with options for logging out or exiting the application.
     */
    private void showSettingsMenu(Component invoker) {
        JPopupMenu settingsMenu = new JPopupMenu();

        JMenuItem logoutItem = new JMenuItem("Logout");
        logoutItem.setFont(new Font("SansSerif", Font.PLAIN, 12));
        logoutItem.addActionListener(e -> handleLogout());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setFont(new Font("SansSerif", Font.PLAIN, 12));
        exitItem.addActionListener(e -> handleExit());

        settingsMenu.add(logoutItem);
        settingsMenu.addSeparator();
        settingsMenu.add(exitItem);

        settingsMenu.show(invoker, 0, invoker.getHeight());
    }

    /**
     * Shuts down active background timers across all child panels.
     */
    private void cleanupTimers() {
        if (missionsJPanel != null) missionsJPanel.stopTimer();
        if (analyticsJPanel != null) analyticsJPanel.stopAutoRefreshTimer();
        if (foundryJPanel != null) foundryJPanel.stopProgressTimer();
    }

    /**
     * Handles user logout flow, cleaning up timers, closing the session, and returning to the login window.
     */
    private void handleLogout() {
        boolean confirmed = UIUtils.showConfirmDialog(
                this,
                "Are you sure you want to log out?",
                "Logout"
        );

        if (confirmed) {
            cleanupTimers();
            try {
                authService.logout(userId);
            } catch (Exception e) {
                UIUtils.showMessageDialog(this, "Error on loging out: " + e.getMessage(), "Logout Error", JOptionPane.ERROR_MESSAGE);
            }
            dispose();
            new LoginJFrame(authService);
        }
    }

    /**
     * Handles application exit, terminating background tasks and shutting down cleanly.
     */
    private void handleExit() {
        boolean confirmed = UIUtils.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Exit"
        );

        if (confirmed) {
            cleanupTimers();
            try {
                authService.logout(userId);
            } catch (Exception e) {
                System.err.println("Error on shutdown logout: " + e.getMessage());
            }
            System.exit(0);
        }
    }
}