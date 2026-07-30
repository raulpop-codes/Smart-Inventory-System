package org.example.ui;

import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.InventoryService;
import org.example.util.UIUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InventoryJPanel extends JPanel {

    private final MainFrame mainFrame;
    private JTable inventoryTable;
    private DefaultTableModel tableModel;

    private JComboBox<String> categoryComboBox;
    private JTextField searchTextField;
    private JLabel creditsLabel;

    public InventoryJPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
    }

    /**
     * Initializes UI components, search fields, category selectors, and table structure.
     */
    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));

        creditsLabel = new JLabel("Credits: 0");
        creditsLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        creditsLabel.setForeground(new Color(212, 175, 55));
        topPanel.add(creditsLabel);

        topPanel.add(new JSeparator(SwingConstants.VERTICAL));

        topPanel.add(new JLabel("Search:"));
        searchTextField = new JTextField(12);

        searchTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshData();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshData();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshData();
            }
        });

        topPanel.add(searchTextField);

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> refreshData());
        topPanel.add(searchButton);

        topPanel.add(new JLabel("Category:"));
        categoryComboBox = new JComboBox<>(new String[]{
                "ALL",
                "ARCHGUN",
                "ARCHMELEE",
                "COMPANION",
                "COMPONENT",
                "MELEE",
                "PRIMARY",
                "RESOURCE",
                "SECONDARY",
                "WARFRAME"
        });
        categoryComboBox.addActionListener(e -> refreshData());
        topPanel.add(categoryComboBox);

        JButton clearButton = new JButton("Reset");
        clearButton.addActionListener(e -> {
            searchTextField.setText("");
            categoryComboBox.setSelectedIndex(0);
            refreshData();
        });
        topPanel.add(clearButton);

        add(topPanel, BorderLayout.NORTH);

        String[] columns = {"Resource ID", "Name", "Quantity"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        inventoryTable = new JTable(tableModel);
        inventoryTable.getTableHeader().setReorderingAllowed(false);
        inventoryTable.getTableHeader().setResizingAllowed(false);

        add(new JScrollPane(inventoryTable), BorderLayout.CENTER);

        refreshData();
    }

    /**
     * Fetches inventory components and credit balances asynchronously using SwingWorker.
     */
    public void refreshData() {
        InventoryService inventoryService = mainFrame.getInventoryService();
        if (inventoryService == null) return;

        new SwingWorker<InventoryDataHolder, Void>() {
            private Exception error = null;

            @Override
            protected InventoryDataHolder doInBackground() {
                InventoryDataHolder data = new InventoryDataHolder();
                try {
                    Optional<ResourceComponent> creditsComp = inventoryService.getComponent("Credits");
                    data.credits = creditsComp.map(ResourceComponent::getQuantity).orElse(0L);

                    String selectedCategory = (String) categoryComboBox.getSelectedItem();
                    if ("ALL".equals(selectedCategory) || selectedCategory == null) {
                        data.components = inventoryService.getAllComponents();
                    } else {
                        data.components = inventoryService.getComponentsByCategory(selectedCategory);
                    }
                } catch (Exception e) {
                    this.error = e;
                    System.err.println("Error loading inventory data: " + e.getMessage());
                }
                return data;
            }

            @Override
            protected void done() {
                if (error != null) {
                    UIUtils.showMessageDialog(mainFrame, "Failed to load inventory: " + error.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    InventoryDataHolder data = get();
                    creditsLabel.setText(String.format("Credits: %,d", data.credits));

                    tableModel.setRowCount(0);
                    String searchText = searchTextField.getText().trim().toLowerCase();

                    for (ResourceComponent comp : data.components) {
                        if (comp.getQuantity() <= 0 || "Credits".equalsIgnoreCase(comp.getName())) {
                            continue;
                        }

                        if (!searchText.isEmpty()) {
                            String name = comp.getName().toLowerCase();
                            String id = comp.getID().toLowerCase();

                            if (!name.contains(searchText) && !id.contains(searchText)) {
                                continue;
                            }
                        }

                        tableModel.addRow(new Object[]{
                                comp.getID(),
                                comp.getName(),
                                String.format("%,d", comp.getQuantity())
                        });
                    }
                } catch (Exception e) {
                    UIUtils.showMessageDialog(mainFrame, "Error updating inventory table: " + e.getMessage(), "UI Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Helper holder class for background inventory data retrieval.
     */
    private static class InventoryDataHolder {
        long credits = 0;
        List<ResourceComponent> components = new ArrayList<>();
    }
}