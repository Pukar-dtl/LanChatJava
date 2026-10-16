package com.lanchat.ui;

import javax.swing.*;
import java.awt.*;

public class LoginDialog extends JDialog {

    private final JTextField serverField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("5050", 6);
    private final JTextField userField = new JTextField(System.getProperty("user.name"), 12);
    private final JTextField dbHostField = new JTextField("127.0.0.1", 12);
    private final JTextField dbPortField = new JTextField("3306", 6);
    private final JTextField dbNameField = new JTextField("lanchat", 10);
    private final JTextField dbUserField = new JTextField("chatuser", 12);
    private final JPasswordField dbPassField = new JPasswordField(12);

    private boolean confirmed;

    public LoginDialog(Frame owner) {
        super(owner, "LAN Chat — Connect", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;

        int y = 0;
        addRow(form, g, y++, "Display name:", userField);

        form.add(sectionLabel("Server / LAN"), g(0, y++, 2));
        addRow(form, g, y++, "Chat server host:", serverField);
        addRow(form, g, y++, "Chat server port:", portField);

        form.add(sectionLabel("MariaDB"), g(0, y++, 2));
        addRow(form, g, y++, "DB host:", dbHostField);
        addRow(form, g, y++, "DB port:", dbPortField);
        addRow(form, g, y++, "Database:", dbNameField);
        addRow(form, g, y++, "DB user:", dbUserField);
        addRow(form, g, y++, "DB password:", dbPassField);

        JButton connect = new JButton("Connect");
        JButton cancel = new JButton("Cancel");
        connect.addActionListener(e -> { confirmed = true; dispose(); });
        cancel.addActionListener(e -> { confirmed = false; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(connect);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(connect);
    }

    private JLabel sectionLabel(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setForeground(new Color(0x2A5DB0));
        return l;
    }

    private GridBagConstraints g(int x, int y, int w) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y; c.gridwidth = w;
        c.insets = new Insets(6, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private void addRow(JPanel p, GridBagConstraints g, int y, String label, JComponent field) {
        g.gridx = 0; g.gridy = y; g.gridwidth = 1;
        g.anchor = GridBagConstraints.WEST;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        p.add(field, g);
        g.fill = GridBagConstraints.NONE;
        g.weightx = 0;
    }

    public boolean isConfirmed() { return confirmed; }

    public String getUsername() { return userField.getText().trim(); }
    public String getServerHost() { return serverField.getText().trim(); }
    public int getServerPort() { return Integer.parseInt(portField.getText().trim()); }
    public String getDbHost() { return dbHostField.getText().trim(); }
    public int getDbPort() { return Integer.parseInt(dbPortField.getText().trim()); }
    public String getDbName() { return dbNameField.getText().trim(); }
    public String getDbUser() { return dbUserField.getText().trim(); }
    public String getDbPass() { return new String(dbPassField.getPassword()); }
}