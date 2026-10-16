package com.lanchat;

import com.lanchat.crypto.CryptoUtil;
import com.lanchat.db.DatabaseManager;
import com.lanchat.net.ChatServer;
import com.lanchat.ui.ChatFrame;
import com.lanchat.ui.LoginDialog;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) throws Exception {
        // Native-ish look
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        CryptoUtil.init();

        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);
            if (!login.isConfirmed()) System.exit(0);

            String name = login.getUsername();
            if (name.isBlank()) { JOptionPane.showMessageDialog(null, "Username required"); System.exit(1); }

            DatabaseManager db = new DatabaseManager(
                    login.getDbHost(), login.getDbPort(), login.getDbName(),
                    login.getDbUser(), login.getDbPass());

            try {
                db.testConnection();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Cannot connect to MariaDB:\n" + ex.getMessage(),
                        "DB Error", JOptionPane.ERROR_MESSAGE);
                System.exit(2);
            }

            int choice = JOptionPane.showOptionDialog(null,
                    "Start as host or join an existing chat server?",
                    "Mode",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new Object[]{"Host", "Join"}, "Join");

            ChatServer server = null;
            if (choice == 0) {
                try {
                    server = new ChatServer(login.getServerPort(), db, new ChatServer.ServerListener() {
                        @Override public void onLog(String line) { System.out.println("[server] " + line); }
                        @Override public void onClientCount(int count) { System.out.println("[server] clients=" + count); }
                    });
                    server.start();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null,
                            "Could not start server on port " + login.getServerPort()
                                    + ":\n" + ex.getMessage(),
                            "Server Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(3);
                }
            }

            ChatFrame frame = new ChatFrame(name,
                    login.getServerHost(), login.getServerPort(), db, server);
            frame.setVisible(true);
        });
    }
}