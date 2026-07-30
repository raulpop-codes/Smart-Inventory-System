package org.example.ui;

import org.example.model.inventory.Blueprint;
import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.FoundryService;
import org.example.service.inventory.InventoryService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

public class AnalyticsJPanel extends JPanel {

    private final MainFrame mainFrame;

    private JLabel totalCreditsLabel;
    private JLabel totalItemsLabel;

    private DefaultTableModel resourceRequirementsModel;
    private JTable resourceRequirementsTable;

    private DefaultTableModel blueprintsStatusModel;
    private JTable blueprintsStatusTable;

    private Timer autoRefreshTimer;

    public AnalyticsJPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        startAutoRefreshTimer();
    }

    /**
     * Initializes UI components, layout settings, and data tabs.
     */
    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel topContainer = new JPanel(new BorderLayout(10, 10));

        JLabel headerLabel = new JLabel("Analytics & Resource Requirement Engine");
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        topContainer.add(headerLabel, BorderLayout.NORTH);

        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        totalCreditsLabel = new JLabel("Total Credits: 0");
        totalCreditsLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        totalCreditsLabel.setForeground(new Color(212, 175, 55));

        totalItemsLabel = new JLabel("Unique Items Stocked: 0");
        totalItemsLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        statsPanel.add(totalCreditsLabel);
        statsPanel.add(totalItemsLabel);
        topContainer.add(statsPanel, BorderLayout.SOUTH);

        add(topContainer, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabbedPane.addTab("Aggregated Resources Needed", createAggregatedResourcesPanel());
        tabbedPane.addTab("Uncrafted Blueprints Overview", createUncraftedBlueprintsPanel());

        add(tabbedPane, BorderLayout.CENTER);

        refreshData();
    }

    /**
     * Creates the panel containing the aggregated resources requirements table.
     */
    private JPanel createAggregatedResourcesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        String[] columns = {"Resource Name / ID", "Available in Inventory", "Total Needed for BPs", "Status / Deficit"};
        resourceRequirementsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        resourceRequirementsTable = new JTable(resourceRequirementsModel);
        resourceRequirementsTable.setRowHeight(28);
        resourceRequirementsTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());

        panel.add(new JScrollPane(resourceRequirementsTable), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the panel containing the uncrafted blueprints overview table.
     */
    private JPanel createUncraftedBlueprintsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        String[] columns = {"Blueprint Name", "Type", "Credit Cost", "Craftability Status", "Missing Resources"};
        blueprintsStatusModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        blueprintsStatusTable = new JTable(blueprintsStatusModel);
        blueprintsStatusTable.setRowHeight(28);
        blueprintsStatusTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
        blueprintsStatusTable.getColumnModel().getColumn(4).setPreferredWidth(300);

        panel.add(new JScrollPane(blueprintsStatusTable), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Starts the auto-refresh timer to update analytics data periodically.
     */
    private void startAutoRefreshTimer() {
        autoRefreshTimer = new Timer(5000, e -> refreshData());
        autoRefreshTimer.start();
    }

    /**
     * Stops the auto-refresh timer when leaving the panel.
     */
    public void stopAutoRefreshTimer() {
        if (autoRefreshTimer != null && autoRefreshTimer.isRunning()) {
            autoRefreshTimer.stop();
        }
    }

    /**
     * Fetches inventory and blueprint data asynchronously using SwingWorker.
     */
    public void refreshData() {
        InventoryService inventoryService = mainFrame.getInventoryService();
        FoundryService foundryService = mainFrame.getFoundryService();

        if (inventoryService == null || foundryService == null) return;

        new SwingWorker<Void, Void>() {
            private long credits = 0;
            private int uniqueItems = 0;
            private final Map<String, Long> inventoryMap = new HashMap<>();
            private final Map<String, String> reqIdToNameMap = new HashMap<>();
            private List<Blueprint> activeBlueprints = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                for (ResourceComponent comp : inventoryService.getAllComponents()) {
                    if ("Credits".equalsIgnoreCase(comp.getName()) || "CREDITS".equalsIgnoreCase(comp.getID())) {
                        credits = comp.getQuantity();
                    } else if (comp.getQuantity() > 0) {
                        uniqueItems++;
                    }
                    inventoryMap.put(comp.getID(), comp.getQuantity());
                    reqIdToNameMap.put(comp.getID(), comp.getName());
                }

                activeBlueprints = foundryService.getAllActiveBlueprints();
                return null;
            }

            @Override
            protected void done() {
                totalCreditsLabel.setText(String.format("Total Credits: %,d", credits));
                totalItemsLabel.setText("Unique Items Stocked: " + uniqueItems);

                populateAggregatedTab(activeBlueprints, inventoryMap, reqIdToNameMap);
                populateBlueprintsTab(activeBlueprints, inventoryMap, reqIdToNameMap, credits);
            }
        }.execute();
    }

    /**
     * Populates the aggregated resources table with total requirements versus available inventory.
     */
    private void populateAggregatedTab(List<Blueprint> blueprints, Map<String, Long> inventoryMap, Map<String, String> reqIdToNameMap) {
        Map<String, Long> requiredMap = new HashMap<>();

        for (Blueprint bp : blueprints) {
            if (bp.isUncrafted() && bp.getRequirements() != null) {
                for (ResourceComponent req : bp.getRequirements()) {
                    requiredMap.put(req.getID(), requiredMap.getOrDefault(req.getID(), 0L) + req.getQuantity());
                }
            }
        }

        resourceRequirementsModel.setRowCount(0);
        for (Map.Entry<String, Long> entry : requiredMap.entrySet()) {
            String reqId = entry.getKey();
            long needed = entry.getValue();
            long have = inventoryMap.getOrDefault(reqId, 0L);
            long deficit = needed - have;

            String status = (deficit <= 0) ? "READY" : String.format("Missing %,d", deficit);
            String name = reqIdToNameMap.getOrDefault(reqId, reqId);

            resourceRequirementsModel.addRow(new Object[]{name, String.format("%,d", have), String.format("%,d", needed), status});
        }
    }

    /**
     * Populates the uncrafted blueprints overview table with craftability status and missing items.
     */
    private void populateBlueprintsTab(List<Blueprint> blueprints, Map<String, Long> inventoryMap, Map<String, String> reqIdToNameMap, long credits) {
        blueprintsStatusModel.setRowCount(0);

        for (Blueprint bp : blueprints) {
            if (!bp.isUncrafted()) continue;

            List<String> missing = new ArrayList<>();
            if (credits < bp.getCreditCost()) {
                missing.add(String.format("Credits (%,d)", bp.getCreditCost() - credits));
            }

            if (bp.getRequirements() != null) {
                for (ResourceComponent req : bp.getRequirements()) {
                    long available = inventoryMap.getOrDefault(req.getID(), 0L);
                    if (available < req.getQuantity()) {
                        String resName = (req.getName() != null && !req.getName().isEmpty()) ? req.getName() : reqIdToNameMap.getOrDefault(req.getID(), req.getID());
                        missing.add(String.format("%s (%,d)", resName, req.getQuantity() - available));
                    }
                }
            }

            boolean isCraftable = missing.isEmpty();
            String status = isCraftable ? "READY" : "NOT READY";
            String missingText = isCraftable ? "None (All requirements met)" : String.join(", ", missing);

            blueprintsStatusModel.addRow(new Object[]{
                    bp.getName(),
                    bp.getType() != null ? bp.getType().name() : "N/A",
                    String.format("%,d", bp.getCreditCost()),
                    status,
                    missingText
            });
        }
    }

    /**
     * Custom renderer to highlight status columns (READY / NOT READY / Missing).
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object val, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, val, sel, focus, row, col);
            String str = (val != null) ? val.toString() : "";

            if ("READY".equalsIgnoreCase(str)) {
                c.setForeground(new Color(46, 204, 113));
                setFont(getFont().deriveFont(Font.BOLD));
            } else if ("NOT READY".equalsIgnoreCase(str) || str.startsWith("Missing")) {
                c.setForeground(new Color(231, 76, 60));
                setFont(getFont().deriveFont(Font.PLAIN));
            } else {
                c.setForeground(table.getForeground());
            }
            return c;
        }
    }
}