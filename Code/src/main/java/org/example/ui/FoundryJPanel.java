package org.example.ui;

import org.example.model.inventory.Blueprint;
import org.example.service.inventory.FoundryService;
import org.example.util.UIUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

public class FoundryJPanel extends JPanel {

    private final MainFrame mainFrame;
    private JTable foundryTable;
    private DefaultTableModel tableModel;

    private JComboBox<String> categoryComboBox;
    private JTextField searchTextField;
    private Timer uiUpdateTimer;

    private volatile List<Blueprint> cachedBlueprints = new ArrayList<>();

    public FoundryJPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        loadDataFromService();
        startProgressTimer();
    }

    /**
     * Initializes UI components and layout layout configurations.
     */
    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();

        topPanel.add(new JLabel("Search:"));
        searchTextField = new JTextField(12);

        searchTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTableUI();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTableUI();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateTableUI();
            }
        });

        topPanel.add(searchTextField);

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> updateTableUI());
        topPanel.add(searchButton);

        topPanel.add(new JLabel("Type:"));
        categoryComboBox = new JComboBox<>(new String[]{"ALL", "WEAPON", "WARFRAME"});
        categoryComboBox.addActionListener(e -> loadDataFromService());
        topPanel.add(categoryComboBox);

        JButton clearButton = new JButton("Reset");
        clearButton.addActionListener(e -> {
            searchTextField.setText("");
            categoryComboBox.setSelectedIndex(0);
            loadDataFromService();
        });
        topPanel.add(clearButton);

        add(topPanel, BorderLayout.NORTH);

        String[] columns = {"Blueprint Name", "Type", "Status / Timer", "Progress", "Action"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4;
            }
        };

        foundryTable = new JTable(tableModel);
        foundryTable.setRowHeight(32);
        foundryTable.getTableHeader().setReorderingAllowed(false);
        foundryTable.getTableHeader().setResizingAllowed(false);

        foundryTable.getColumnModel().getColumn(3).setCellRenderer(new ProgressBarRenderer());
        foundryTable.getColumnModel().getColumn(4).setCellRenderer(new ButtonRenderer());
        foundryTable.getColumnModel().getColumn(4).setCellEditor(new ButtonEditor(new JCheckBox()));

        add(new JScrollPane(foundryTable), BorderLayout.CENTER);
    }

    /**
     * Starts the background timer to update progress bars and timers every second.
     */
    private void startProgressTimer() {
        uiUpdateTimer = new Timer(1000, e -> updateTableUI());
        uiUpdateTimer.start();
    }

    /**
     * Stops the progress timer when leaving the panel.
     */
    public void stopProgressTimer() {
        if (uiUpdateTimer != null && uiUpdateTimer.isRunning()) {
            uiUpdateTimer.stop();
        }
    }

    /**
     * Loads blueprint data asynchronously using SwingWorker to prevent UI freezing.
     */
    public void loadDataFromService() {
        FoundryService foundryService = mainFrame.getFoundryService();
        if (foundryService == null) return;

        new SwingWorker<List<Blueprint>, Void>() {
            private Exception error = null;

            @Override
            protected List<Blueprint> doInBackground() {
                try {
                    String selectedCategory = (String) categoryComboBox.getSelectedItem();
                    if ("ALL".equals(selectedCategory) || selectedCategory == null) {
                        return foundryService.getAllActiveBlueprints();
                    } else {
                        return foundryService.getBlueprintsByCategory(selectedCategory);
                    }
                } catch (Exception e) {
                    this.error = e;
                    return new ArrayList<>();
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    UIUtils.showMessageDialog(mainFrame, "Failed to load foundry data: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    List<Blueprint> result = get();
                    if (result != null) {
                        cachedBlueprints = new ArrayList<>(result);
                    }
                    updateTableUI();
                } catch (Exception e) {
                    UIUtils.showMessageDialog(mainFrame, "Error rendering foundry data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Updates the table rows and values based on the search query and cached blueprints.
     */
    private void updateTableUI() {
        int selectedRow = foundryTable.getSelectedRow();
        tableModel.setRowCount(0);

        String searchText = searchTextField.getText().trim().toLowerCase();
        LocalDateTime now = LocalDateTime.now();

        List<Blueprint> safeList = new ArrayList<>(cachedBlueprints);

        for (Blueprint bp : safeList) {
            if (!searchText.isEmpty()) {
                String name = bp.getName().toLowerCase();
                String id = bp.getID().toLowerCase();
                if (!name.contains(searchText) && !id.contains(searchText)) {
                    continue;
                }
            }

            String statusText;
            int progressPercent = 0;

            if (bp.isCrafting()) {
                LocalDateTime finishedAt = bp.getCraftingFinishedAt();
                if (finishedAt != null && now.isAfter(finishedAt)) {
                    statusText = "Ready to Claim!";
                    progressPercent = 100;
                } else {
                    statusText = bp.getRemainingTimeString(now);
                    if (finishedAt != null) {
                        long totalSeconds = bp.getType().getHoursNeeded() * 3600L;
                        long remainingSeconds = Duration.between(now, finishedAt).getSeconds();
                        long elapsedSeconds = totalSeconds - remainingSeconds;
                        progressPercent = (int) Math.max(0, Math.min(100, ((double) elapsedSeconds / totalSeconds) * 100));
                    }
                }
            } else if (bp.isCrafted()) {
                statusText = "Crafted";
                progressPercent = 100;
            } else {
                statusText = "Ready to Build";
            }

            tableModel.addRow(new Object[]{
                    bp.getName(),
                    bp.getType() != null ? bp.getType().name() : "N/A",
                    statusText,
                    progressPercent,
                    bp
            });
        }

        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
            foundryTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }

    /**
     * Renderer class to display progress bars inside table cells.
     */
    private static class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressBarRenderer() {
            setMinimum(0);
            setMaximum(100);
            setStringPainted(true);
            setForeground(new Color(46, 204, 113));
            setBackground(Color.DARK_GRAY);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            int progress = (value instanceof Integer) ? (Integer) value : 0;
            setValue(progress);
            setString(progress + "%");
            return this;
        }
    }

    /**
     * Renderer class for action buttons inside the table.
     */
    private class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof Blueprint bp) {
                if (bp.isUncrafted()) {
                    setText("Build");
                    setBackground(new Color(52, 152, 219));
                    setForeground(Color.WHITE);
                    setEnabled(true);
                } else if (bp.isCrafting()) {
                    LocalDateTime finishedAt = bp.getCraftingFinishedAt();
                    if (finishedAt != null && LocalDateTime.now().isAfter(finishedAt)) {
                        setText("Claim");
                        setBackground(new Color(46, 204, 113));
                        setForeground(Color.WHITE);
                    } else {
                        setText("Cancel");
                        setBackground(new Color(231, 76, 60));
                        setForeground(Color.WHITE);
                    }
                    setEnabled(true);
                } else {
                    setText("Done");
                    setBackground(Color.LIGHT_GRAY);
                    setForeground(Color.DARK_GRAY);
                    setEnabled(false);
                }
            }
            return this;
        }
    }

    /**
     * Editor class that handles actions when clicking the table action buttons.
     */
    private class ButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private Blueprint currentBp;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);

            button.addActionListener(e -> {
                fireEditingStopped();
                if (currentBp == null) return;

                boolean actionApproved = false;
                String actionType = "";

                if (currentBp.isUncrafted()) {
                    actionType = "BUILD";
                    actionApproved = handleConfirmation("start crafting", currentBp.getName());
                } else if (currentBp.isCrafting()) {
                    LocalDateTime finishedAt = currentBp.getCraftingFinishedAt();
                    if (finishedAt != null && LocalDateTime.now().isAfter(finishedAt)) {
                        actionType = "CLAIM";
                        actionApproved = true;
                    } else {
                        actionType = "CANCEL";
                        actionApproved = handleConfirmation("cancel crafting for", currentBp.getName());
                    }
                }

                if (!actionApproved) {
                    return;
                }

                final String finalAction = actionType;
                FoundryService foundryService = mainFrame.getFoundryService();

                new SwingWorker<Void, Void>() {
                    private String messageToShow = null;
                    private String errorMsg = null;

                    @Override
                    protected Void doInBackground() {
                        try {
                            switch (finalAction) {
                                case "BUILD":
                                    foundryService.startCrafting(currentBp.getID());
                                    messageToShow = "Started crafting: " + currentBp.getName();
                                    break;
                                case "CLAIM":
                                    foundryService.claimCraftedItem(currentBp.getID());
                                    messageToShow = "Claimed: " + currentBp.getName();
                                    break;
                                case "CANCEL":
                                    foundryService.cancelCrafting(currentBp.getID());
                                    messageToShow = "Cancelled crafting for: " + currentBp.getName();
                                    break;
                                default:
                                    break;
                            }
                        } catch (Exception ex) {
                            errorMsg = ex.getMessage();
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        if (errorMsg != null) {
                            UIUtils.showMessageDialog(mainFrame, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
                        } else if (messageToShow != null) {
                            UIUtils.showMessageDialog(mainFrame, messageToShow, "Success", JOptionPane.INFORMATION_MESSAGE);
                        }
                        loadDataFromService();
                    }
                }.execute();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            if (value instanceof Blueprint bp) {
                currentBp = bp;
                if (bp.isUncrafted()) {
                    button.setText("Build");
                    button.setBackground(new Color(52, 152, 219));
                } else if (bp.isCrafting()) {
                    LocalDateTime finishedAt = bp.getCraftingFinishedAt();
                    if (finishedAt != null && LocalDateTime.now().isAfter(finishedAt)) {
                        button.setText("Claim");
                        button.setBackground(new Color(46, 204, 113));
                    } else {
                        button.setText("Cancel");
                        button.setBackground(new Color(231, 76, 60));
                    }
                }
                button.setForeground(Color.WHITE);
            }
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return currentBp;
        }

        @Override
        public boolean isCellEditable(EventObject anEvent) {
            return true;
        }
    }

    /**
     * Prompts the user with a confirmation dialog before executing a critical action.
     */
    private boolean handleConfirmation(String option, String bpName) {
        String message = String.format("Are you sure you want to %s %s?", option, bpName);
        String title = "Confirmation";

        return UIUtils.showConfirmDialog(this.mainFrame, message, title);
    }
}