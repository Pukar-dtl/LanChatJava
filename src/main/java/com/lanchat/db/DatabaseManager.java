package com.lanchat.db;

import com.lanchat.crypto.CryptoUtil;
import com.lanchat.model.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(String host, int port, String db,
                           String user, String password) {
        this.url = "jdbc:mariadb://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=utf8mb4";
        this.user = user;
        this.password = password;
    }

    public void testConnection() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.isValid(2);
        }
    }

    public long saveMessage(String sender, String recipient, String plaintext) throws SQLException {
        CryptoUtil.Encrypted enc = CryptoUtil.encrypt(plaintext);
        String sql = "INSERT INTO messages(sender, recipient, ciphertext, iv) VALUES (?,?,?,?)";
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sender);
            ps.setString(2, recipient == null ? "ALL" : recipient);
            ps.setString(3, enc.ciphertextB64());
            ps.setString(4, enc.ivB64());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public List<Message> fetchHistory(String me, String peer, int limit) throws SQLException {
        // Returns messages where (sender=me and recipient=peer) OR (sender=peer and recipient=me)
        // For broadcast, peer == "ALL"
        String sql = """
            SELECT id, sender, recipient, ciphertext, iv, sent_at
            FROM messages
            WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)
            ORDER BY id DESC LIMIT ?
            """;
        List<Message> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, me);
            ps.setString(2, peer);
            ps.setString(3, peer);
            ps.setString(4, me);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String plain = CryptoUtil.decrypt(
                            rs.getString("ciphertext"), rs.getString("iv"));
                    out.add(new Message(
                            rs.getLong("id"),
                            rs.getString("sender"),
                            rs.getString("recipient"),
                            plain,
                            rs.getTimestamp("sent_at").toLocalDateTime()));
                }
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }
}