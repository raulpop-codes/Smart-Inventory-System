package org.example.util;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;

public class UIUtils {

    /**
     * Displays an undecorated modal dialog for messages (Information, Warning, Error).
     */
    public static void showMessageDialog(Window owner, String message, String title, int messageType) {
        JDialog dialog = createBaseDialog(owner, title);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                getBorderForType(messageType),
                BorderFactory.createEmptyBorder(10, 15, 12, 15)
        ));

        // Header for Title (since undecorated windows lack a native title bar)
        if (title != null && !title.isBlank()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            contentPanel.add(titleLabel, BorderLayout.NORTH);
        }

        // Message body
        JLabel messageLabel = new JLabel("<html><body style='width: 200px; text-align: center;'>" + message + "</body></html>");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(messageLabel, BorderLayout.CENTER);

        // OK button
        JButton okButton = new JButton("OK");
        okButton.setPreferredSize(new Dimension(80, 25));
        okButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonPanel.add(okButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(contentPanel);
        dialog.getRootPane().setDefaultButton(okButton);
        registerEscapeKey(dialog, () -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * Displays an undecorated modal confirmation dialog (YES / NO).
     * Returns true if the user clicked Yes.
     */
    public static boolean showConfirmDialog(Window owner, String message, String title) {
        JDialog dialog = createBaseDialog(owner, title);
        final boolean[] result = {false};

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 2),
                BorderFactory.createEmptyBorder(10, 15, 12, 15)
        ));

        // Header for Title
        if (title != null && !title.isBlank()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            contentPanel.add(titleLabel, BorderLayout.NORTH);
        }

        JLabel messageLabel = new JLabel("<html><body style='width: 200px; text-align: center;'>" + message + "</body></html>");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(messageLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton yesButton = new JButton("Yes");
        JButton noButton = new JButton("No");

        yesButton.setPreferredSize(new Dimension(75, 25));
        noButton.setPreferredSize(new Dimension(75, 25));

        yesButton.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });

        noButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(contentPanel);
        dialog.getRootPane().setDefaultButton(yesButton);
        registerEscapeKey(dialog, () -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);

        return result[0];
    }

    /**
     * Displays a Toast notification in the bottom-right corner of the main window or screen.
     */
    public static void showToastBottomRight(Window owner, String title, String htmlMessage, Color borderColor) {
        SwingUtilities.invokeLater(() -> {
            JWindow toast = new JWindow(owner);

            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBackground(new Color(45, 52, 54));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor, 2),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)
            ));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            titleLabel.setForeground(borderColor);

            JLabel msgLabel = new JLabel("<html><body style='width: 220px; color: white;'>" + htmlMessage + "</body></html>");
            msgLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            panel.add(titleLabel, BorderLayout.NORTH);
            panel.add(msgLabel, BorderLayout.CENTER);

            toast.getContentPane().add(panel);
            toast.pack();

            if (owner != null && owner.isShowing()) {
                Rectangle bounds = owner.getBounds();
                int x = bounds.x + bounds.width - toast.getWidth() - 20;
                int y = bounds.y + bounds.height - toast.getHeight() - 20;
                toast.setLocation(x, y);
            } else {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                toast.setLocation(screenSize.width - toast.getWidth() - 20, screenSize.height - toast.getHeight() - 40);
            }

            toast.setAlwaysOnTop(true);
            toast.setVisible(true);

            Timer timer = new Timer(4000, e -> toast.dispose());
            timer.setRepeats(false);
            timer.start();
        });
    }

    /**
     * Creates an undecorated base dialog with the correct owner hierarchy.
     */
    private static JDialog createBaseDialog(Window owner, String title) {
        JDialog dialog;
        if (owner instanceof Frame frame) {
            dialog = new JDialog(frame, title, true);
        } else if (owner instanceof Dialog d) {
            dialog = new JDialog(d, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }

        dialog.setUndecorated(true);
        return dialog;
    }

    /**
     * Registers the Escape key binding to close the dialog.
     */
    private static void registerEscapeKey(JDialog dialog, Runnable action) {
        dialog.getRootPane().registerKeyboardAction(
                e -> action.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    /**
     * Returns an appropriate border color based on the message dialog type.
     */
    private static Border getBorderForType(int messageType) {
        Color borderColor = switch (messageType) {
            case JOptionPane.WARNING_MESSAGE -> new Color(220, 160, 0);
            case JOptionPane.ERROR_MESSAGE -> new Color(200, 50, 50);
            default -> new Color(70, 130, 180);
        };
        return BorderFactory.createLineBorder(borderColor, 2);
    }
}