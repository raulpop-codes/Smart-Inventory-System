package org.example.ui;

import org.example.model.enums.MissionState;
import org.example.model.inventory.ResourceComponent;
import org.example.model.mission.Mission;
import org.example.service.mission.MissionService;
import org.example.util.UIUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MissionsJPanel extends JPanel {

    private final MainFrame mainFrame;
    private final MissionService missionService;
    private javax.swing.Timer uiRefreshTimer;
    private JPanel missionsGrid;
    private List<Mission> cachedMissions = new ArrayList<>();

    public MissionsJPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.missionService = mainFrame.getMissionService();
        initComponents();
        loadMissionsAsync();
        startTimer();
    }

    /**
     * Initializes components, layout bounds, and header configuration.
     */
    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel headerLabel = new JLabel("Solar System Missions & Sector Operations", SwingConstants.LEFT);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(headerLabel, BorderLayout.NORTH);

        missionsGrid = new JPanel(new GridLayout(1, 3, 15, 15));
        add(missionsGrid, BorderLayout.CENTER);
    }

    /**
     * Loads active missions asynchronously using SwingWorker.
     */
    public void loadMissionsAsync() {
        new SwingWorker<List<Mission>, Void>() {
            private Exception error = null;

            @Override
            protected List<Mission> doInBackground() {
                try {
                    return missionService.getOrGenerateActiveMissions();
                } catch (Exception e) {
                    this.error = e;
                    return new ArrayList<>();
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    UIUtils.showMessageDialog(mainFrame, "Error loading missions: " + error.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    cachedMissions = get();
                    rebuildGridUI();
                } catch (Exception e) {
                    UIUtils.showMessageDialog(mainFrame, "Error initializing missions grid: " + e.getMessage(), "UI Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Rebuilds the grid UI cards based on currently cached missions.
     */
    private void rebuildGridUI() {
        missionsGrid.removeAll();
        for (Mission mission : cachedMissions) {
            missionsGrid.add(createMissionCard(mission));
        }
        missionsGrid.revalidate();
        missionsGrid.repaint();
        refreshUI();
    }

    /**
     * Creates an individual mission operation card UI component.
     */
    private JPanel createMissionCard(Mission mission) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Operation #" + mission.getSlotIndex(),
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14)
        ));

        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        JLabel titleLabel = new JLabel("<html><b>" + mission.getName() + "</b></html>");
        titleLabel.setForeground(new Color(41, 128, 185));

        JLabel locationLabel = new JLabel("Sector: " + mission.getLocation());
        locationLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel durationLabel = new JLabel("Duration: " + formatMinutes(mission.getDurationMinutes()));
        durationLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));

        infoPanel.add(titleLabel);
        infoPanel.add(locationLabel);
        infoPanel.add(durationLabel);

        JPanel rewardsPanel = new JPanel(new BorderLayout());
        rewardsPanel.setBorder(BorderFactory.createTitledBorder("Guaranteed Rewards"));

        DefaultListModel<String> rewardsListModel = new DefaultListModel<>();

        long creditsQuantity = 0;
        List<ResourceComponent> otherResources = new ArrayList<>();

        if (mission.getRewardResources() != null) {
            creditsQuantity = mission.getRewardResources().stream()
                    .filter(r -> r.getName() != null && r.getName().toLowerCase().contains("credit"))
                    .mapToLong(ResourceComponent::getQuantity)
                    .sum();

            otherResources = mission.getRewardResources().stream()
                    .filter(r -> r.getName() == null || !r.getName().toLowerCase().contains("credit"))
                    .toList();
        }

        // Credits displayed first
        rewardsListModel.addElement("• Credits: " + String.format("%,d", creditsQuantity));

        // Remaining reward materials
        for (ResourceComponent res : otherResources) {
            rewardsListModel.addElement("• Material: " + res.getName() + " x" + String.format("%,d", res.getQuantity()));
        }

        JList<String> rewardsList = new JList<>(rewardsListModel);
        rewardsList.setBackground(card.getBackground());
        rewardsPanel.add(new JScrollPane(rewardsList), BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new BorderLayout(5, 5));
        JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel buttonsGrid = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton deployBtn = new JButton("Deploy");
        JButton cancelBtn = new JButton("Cancel");

        deployBtn.addActionListener(e -> {
            LocalDateTime now = LocalDateTime.now();
            if (mission.isReadyToClaim(now)) {
                handleClaim(mission.getSlotIndex());
            } else if (mission.getState() == MissionState.READY) {
                handleDeploy(mission.getSlotIndex());
            }
        });

        cancelBtn.addActionListener(e -> {
            if (mission.getState() == MissionState.IN_PROGRESS) {
                boolean confirm = UIUtils.showConfirmDialog(mainFrame,
                        "Are you sure you want to cancel this mission?",
                        "Cancel Mission");
                if (confirm) {
                    handleCancel(mission.getSlotIndex());
                }
            }
        });

        buttonsGrid.add(deployBtn);
        buttonsGrid.add(cancelBtn);

        actionPanel.add(statusLabel, BorderLayout.NORTH);
        actionPanel.add(buttonsGrid, BorderLayout.SOUTH);

        card.add(infoPanel, BorderLayout.NORTH);
        card.add(rewardsPanel, BorderLayout.CENTER);
        card.add(actionPanel, BorderLayout.SOUTH);

        card.putClientProperty("statusLabel", statusLabel);
        card.putClientProperty("deployBtn", deployBtn);
        card.putClientProperty("cancelBtn", cancelBtn);
        card.putClientProperty("mission", mission);

        return card;
    }

    /**
     * Handles starting a mission deployment asynchronously.
     */
    private void handleDeploy(int slotIndex) {
        new SwingWorker<Void, Void>() {
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    missionService.startMission(slotIndex);
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    UIUtils.showMessageDialog(mainFrame, error, "Deployment Blocked", JOptionPane.WARNING_MESSAGE);
                }
                loadMissionsAsync();
            }
        }.execute();
    }

    /**
     * Handles cancelling an active mission asynchronously.
     */
    private void handleCancel(int slotIndex) {
        new SwingWorker<Void, Void>() {
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    missionService.cancelMission(slotIndex);
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    UIUtils.showMessageDialog(mainFrame, "Failed to cancel mission: " + error, "Error", JOptionPane.ERROR_MESSAGE);
                }
                loadMissionsAsync();
            }
        }.execute();
    }

    /**
     * Handles claiming completed mission rewards asynchronously.
     */
    private void handleClaim(int slotIndex) {
        new SwingWorker<Void, Void>() {
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    missionService.claimRewards(slotIndex);
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    UIUtils.showMessageDialog(mainFrame, error, "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    UIUtils.showMessageDialog(mainFrame, "Mission Complete! Rewards added to inventory.", "Rewards Claimed", JOptionPane.INFORMATION_MESSAGE);
                }
                loadMissionsAsync();
            }
        }.execute();
    }

    /**
     * Starts the UI refresh timer to tick down mission timers every second.
     */
    private void startTimer() {
        uiRefreshTimer = new javax.swing.Timer(1000, e -> refreshUI());
        uiRefreshTimer.start();
    }

    /**
     * Stops the UI refresh timer when leaving the panel.
     */
    public void stopTimer() {
        if (uiRefreshTimer != null && uiRefreshTimer.isRunning()) {
            uiRefreshTimer.stop();
        }
    }

    /**
     * Updates mission card labels and button states dynamically based on current time.
     */
    private void refreshUI() {
        LocalDateTime now = LocalDateTime.now();
        boolean anyActive = cachedMissions.stream()
                .anyMatch(m -> m.getState() == MissionState.IN_PROGRESS);

        for (Component comp : missionsGrid.getComponents()) {
            if (comp instanceof JPanel card) {
                Mission m = (Mission) card.getClientProperty("mission");
                JLabel statusLabel = (JLabel) card.getClientProperty("statusLabel");
                JButton deployBtn = (JButton) card.getClientProperty("deployBtn");
                JButton cancelBtn = (JButton) card.getClientProperty("cancelBtn");

                if (m == null || statusLabel == null) continue;

                MissionState state = m.getState();
                if (state == null) continue;

                switch (state) {
                    case IN_PROGRESS -> {
                        if (m.isReadyToClaim(now)) {
                            statusLabel.setText("MISSION COMPLETE!");
                            statusLabel.setForeground(new Color(46, 204, 113));

                            deployBtn.setText("Claim");
                            deployBtn.setEnabled(true);
                            cancelBtn.setEnabled(false);
                        } else {
                            statusLabel.setText("In Progress: " + m.getRemainingTimeString(now));
                            statusLabel.setForeground(new Color(230, 126, 34));

                            deployBtn.setText("Deploying...");
                            deployBtn.setEnabled(false);
                            cancelBtn.setEnabled(true);
                        }
                    }
                    case COMPLETED -> {
                        statusLabel.setText("MISSION COMPLETE!");
                        statusLabel.setForeground(new Color(46, 204, 113));

                        deployBtn.setText("Claim");
                        deployBtn.setEnabled(true);
                        cancelBtn.setEnabled(false);
                    }
                    default -> {
                        statusLabel.setText("Ready for Deployment");
                        statusLabel.setForeground(Color.DARK_GRAY);

                        deployBtn.setText("Deploy");
                        deployBtn.setEnabled(!anyActive);
                        cancelBtn.setEnabled(false);
                    }
                }
            }
        }
    }

    /**
     * Formats total minutes into a readable hours and minutes string.
     */
    private String formatMinutes(int totalMinutes) {
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return (h > 0 ? h + "h " : "") + m + "m";
    }
}