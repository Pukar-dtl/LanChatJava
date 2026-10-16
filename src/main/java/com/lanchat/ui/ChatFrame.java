package com.lanchat.ui;

import com.lanchat.db.DatabaseManager;
import com.lanchat.model.Message;
import com.lanchat.net.ChatServer;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatFrame extends JFrame {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String username;
    private final String serverHost;
    private final int serverPort;
    private final DatabaseManager db;

    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(usersModel);
    private final JTextPane chatPane = new JTextPane();
    private final JTextField inputField = new JTextField();
    private final JLabel statusLabel = new JLabel("Connecting…");
    private final StyledDocument doc = chatPane.getStyledDocument();

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-io"); t.setDaemon(true); return t;
    });

    private String currentPeer = "ALL";

    public ChatFrame(String username, String serverHost, int serverPort,
                     DatabaseManager db, ChatServer server) {
        super("LAN Chat — " + username);
        this.username = username;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.db = db;

        buildUi();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        pack();
        setSize(880, 600);
        setLocationRelativeTo(null);

        connect();
        if (server != null) {
            statusLabel.setText("Hosting on port " + serverPort + " — connected as " + username);
        }
    }

    private void buildUi() {
        // for header ig
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        header.setBackground(new Color(0x2A5DB0));
        JLabel title = new JLabel("LAN Chat");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel.setForeground(new Color(0xDDEEFF));
        header.add(title, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);

        // sidebar code (I hope it works or else I quit my degree)
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.setCellRenderer(new UserCellRenderer());
        JScrollPane usersScroll = new JScrollPane(usersList);
        usersScroll.setPreferredSize(new Dimension(200, 0));
        usersScroll.setBorder(BorderFactory.createTitledBorder("Online"));
        usersList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String sel = usersList.getSelectedValue();
            if (sel != null && !sel.equals(username)) {
                currentPeer = "ALL".equals(sel) ? "ALL" : sel;
                reloadHistory();
            }
        });

        // this is where the twits meet the tats
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(0xFAFAFA));
        chatPane.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane chatScroll = new JScrollPane(chatPane);

        inputField.setFont(inputField.getFont().deriveFont(14f));
        JButton send = new JButton("Send");
        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(send, BorderLayout.EAST);

        ActionListener sendAction = e -> sendCurrent();
        send.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        JPanel center = new JPanel(new BorderLayout());
        center.add(chatScroll, BorderLayout.CENTER);
        center.add(inputPanel, BorderLayout.SOUTH);

        JPanel main = new JPanel(new BorderLayout());
        main.add(usersScroll, BorderLayout.WEST);
        main.add(center, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(main, BorderLayout.CENTER);
        usersModel.addElement("ALL");
    }

    // socket code

    private void connect() {
        io.submit(() -> {
            try {
                socket = new Socket(serverHost, serverPort);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                connected = true;
                out.println("LOGIN|" + username);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Connected as " + username));

                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> handleServerLine(finalLine));
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected");
                    appendSystem("Connection error: " + ex.getMessage());
                });
            } finally {
                connected = false;
            }
        });
    }

    private void handleServerLine(String line) {
        String[] p = line.split("\\|", -1);
        switch (p[0]) {
            case "OK" -> appendSystem(p.length > 1 ? p[1] : "OK");
            case "ERROR" -> {
                JOptionPane.showMessageDialog(this, p.length > 1 ? p[1] : "Server error",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            case "USERS" -> {
                String[] names = p.length > 1 && !p[1].isEmpty() ? p[1].split(",") : new String[0];
                usersModel.clear();
                usersModel.addElement("ALL");
                for (String n : names) if (!n.equals(username)) usersModel.addElement(n);
            }
            case "JOIN" -> appendSystem(p[1] + " joined");
            case "LEAVE" -> appendSystem(p[1] + " left");
            case "SYS" -> appendSystem(p[1]);
            case "MSG" -> {
                String sender = p[1];
                String recipient = p[2];
                String ts = p[4];
                String text = p[5];
                appendMessage(sender, recipient, text, ts);
            }
        }
    }

    private void sendCurrent() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || !connected) return;
        out.println("MSG|" + currentPeer + "|" + text);
        inputField.setText("");
    }

    // I keep the read history so someone could look at their ex's text back to back

    private void reloadHistory() {
        chatPane.setText("");
        appendSystem("── history with " + ("ALL".equals(currentPeer) ? "everyone" : currentPeer) + " ──");
        io.submit(() -> {
            try {
                List<Message> history = db.fetchHistory(username, currentPeer, 200);
                SwingUtilities.invokeLater(() -> {
                    for (Message m : history) {
                        appendMessage(m.sender(), m.recipient(), m.plaintext(),
                                m.sentAt().format(TIME_FMT));
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendSystem("History load failed: " + ex.getMessage()));
            }
        });
    }

    private void appendMessage(String sender, String recipient, String text, String ts) {
        boolean mine = sender.equals(username);
        boolean isBroadcast = "ALL".equals(recipient);
        Color nameColor = mine ? new Color(0x1B8A3A) : new Color(0x2A5DB0);

        try {
            SimpleAttributeSet meta = new SimpleAttributeSet();
            StyleConstants.setForeground(meta, Color.GRAY);
            StyleConstants.setFontSize(meta, 11);
            StyleConstants.setItalic(meta, true);
            String head = (isBroadcast && !mine ? "[" + sender + " → all]" : "[" + ts + "] " + (mine ? "me" : sender));
            doc.insertString(doc.getLength(), head + " ", meta);

            SimpleAttributeSet body = new SimpleAttributeSet();
            StyleConstants.setForeground(body, mine ? new Color(0x1B4A8A) : new Color(0x222222));
            StyleConstants.setFontSize(body, 13);
            if (text.contains("**")) {
                String[] parts = text.split("\\*\\*");
                for (int i = 0; i < parts.length; i++) {
                    if (i % 2 == 1) StyleConstants.setBold(body, true);
                    else StyleConstants.setBold(body, false);
                    doc.insertString(doc.getLength(), parts[i], body);
                }
                StyleConstants.setBold(body, false);
            } else {
                doc.insertString(doc.getLength(), text, body);
            }
            doc.insertString(doc.getLength(), "\n", null);
            chatPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void appendSystem(String text) {
        try {
            SimpleAttributeSet s = new SimpleAttributeSet();
            StyleConstants.setForeground(s, new Color(0x777777));
            StyleConstants.setItalic(s, true);
            StyleConstants.setFontSize(s, 11);
            doc.insertString(doc.getLength(), text + "\n", s);
            chatPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void shutdown() {
        try {
            if (out != null) out.println("LOGOUT");
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        io.shutdownNow();
        dispose();
    }

    private static class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String name = String.valueOf(value);
            if ("ALL".equals(name)) {
                l.setText("🌐  Everyone");
                l.setFont(l.getFont().deriveFont(Font.BOLD));
            } else {
                l.setText("👤  " + name);
            }
            l.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return l;
        }
    }
}