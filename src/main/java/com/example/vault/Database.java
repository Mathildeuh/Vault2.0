package com.example.vault;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class Database {
    private final Plugin plugin;
    private HikariDataSource ds;
    private final boolean enabled;

    public Database(Plugin plugin) {
        this.plugin = plugin;
        boolean use = plugin.getConfig().getBoolean("storage.use_mysql", false);
        this.enabled = use;
        if (use) {
            try {
                HikariConfig cfg = new HikariConfig();
                String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
                String db = plugin.getConfig().getString("storage.mysql.database", "vault");
                String user = plugin.getConfig().getString("storage.mysql.username", "root");
                String pass = plugin.getConfig().getString("storage.mysql.password", "");
                int pool = plugin.getConfig().getInt("storage.mysql.pool_size", 10);
                String jdbc = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                cfg.setJdbcUrl(jdbc);
                cfg.setUsername(user);
                cfg.setPassword(pass);
                cfg.setMaximumPoolSize(pool);
                cfg.addDataSourceProperty("cachePrepStmts", "true");
                cfg.addDataSourceProperty("prepStmtCacheSize", "250");
                cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                ds = new HikariDataSource(cfg);
                ensureSchema();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL: " + ex.getMessage(), ex);
                ds = null;
            }
        }
    }

    public boolean isEnabled() {
        return enabled && ds != null;
    }

    private void ensureSchema() throws SQLException {
        try (Connection conn = ds.getConnection()) {
            try (Statement st = conn.createStatement()) {
                // balances table
                st.executeUpdate("CREATE TABLE IF NOT EXISTS vault_balances (uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE NOT NULL)");
                // charge requests: id auto, recipient lower-case name, sender, amount, created_at
                st.executeUpdate("CREATE TABLE IF NOT EXISTS vault_charge_requests (id BIGINT AUTO_INCREMENT PRIMARY KEY, recipient VARCHAR(64) NOT NULL, sender VARCHAR(64) NOT NULL, amount DOUBLE NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            }
        }
    }

    public Map<UUID, Double> loadAllBalances() throws SQLException {
        Map<UUID, Double> map = new HashMap<>();
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT uuid, balance FROM vault_balances")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID u = UUID.fromString(rs.getString("uuid"));
                        double bal = rs.getDouble("balance");
                        map.put(u, bal);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Skipping invalid uuid in DB: " + rs.getString("uuid"));
                    }
                }
            }
        }
        return map;
    }

    public void saveBalance(UUID uuid, double balance) throws SQLException {
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO vault_balances (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = ?")) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, balance);
            ps.setDouble(3, balance);
            ps.executeUpdate();
        }
    }

    public void deleteAllBalances() throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("TRUNCATE TABLE vault_balances");
        }
    }

    public List<ChargeRequest> loadPendingRequests(String recipientLower, int limit) throws SQLException {
        List<ChargeRequest> out = new ArrayList<>();
        String sql = "SELECT id, recipient, sender, amount FROM vault_charge_requests WHERE recipient = ? ORDER BY created_at ASC LIMIT ?";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recipientLower);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ChargeRequest(rs.getLong("id"), rs.getString("recipient"), rs.getString("sender"), rs.getDouble("amount")));
                }
            }
        }
        return out;
    }

    public List<ChargeRequest> loadPendingRequests(String recipientLower) throws SQLException {
        List<ChargeRequest> out = new ArrayList<>();
        String sql = "SELECT id, recipient, sender, amount FROM vault_charge_requests WHERE recipient = ? ORDER BY created_at ASC";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recipientLower);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ChargeRequest(rs.getLong("id"), rs.getString("recipient"), rs.getString("sender"), rs.getDouble("amount")));
                }
            }
        }
        return out;
    }

    public long insertPendingRequest(String recipientLower, String sender, double amount) throws SQLException {
        String sql = "INSERT INTO vault_charge_requests (recipient, sender, amount) VALUES (?, ?, ?)";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, recipientLower);
            ps.setString(2, sender);
            ps.setDouble(3, amount);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        return -1;
    }

    public void deletePendingById(long id) throws SQLException {
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM vault_charge_requests WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void close() {
        if (ds != null) {
            try { ds.close(); } catch (Exception ignored) {}
            ds = null;
        }
    }

    public static class ChargeRequest {
        public final long id;
        public final String recipient;
        public final String sender;
        public final double amount;

        public ChargeRequest(long id, String recipient, String sender, double amount) {
            this.id = id;
            this.recipient = recipient;
            this.sender = sender;
            this.amount = amount;
        }
    }
}

