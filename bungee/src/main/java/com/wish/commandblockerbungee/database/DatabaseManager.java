package com.wish.commandblockerbungee.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final CommandBlockerBungee plugin;
    private final ConfigManager config;
    private HikariDataSource dataSource;
    private String tablePrefix;

    public DatabaseManager(CommandBlockerBungee plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        if (!config.isDatabaseEnabled()) return;

        // Close existing if present (Reload safety)
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }

        String type = config.getDatabaseType();
        this.tablePrefix = config.getDatabaseTablePrefix();

        if (!this.tablePrefix.matches("^[a-zA-Z0-9_]+$")) {
            plugin.getLogger().warning("Invalid table prefix defined in config: '" + this.tablePrefix + "'. Using default 'cb_' to prevent SQL issues.");
            this.tablePrefix = "cb_";
        }

        HikariConfig hikariConfig = new HikariConfig();

        if (type.equalsIgnoreCase("mysql")) {
            hikariConfig.setDriverClassName("com.wish.commandblockerbungee.libs.mysql.cj.jdbc.Driver");
            // Removed hardcoded useSSL=false to allow secure connections if configured in environment/driver defaults
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/" + config.getDatabaseName() + "?autoReconnect=true");
            hikariConfig.setUsername(config.getDatabaseUser());
            hikariConfig.setPassword(config.getDatabasePassword());
            
            // Performance optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        } else {
            hikariConfig.setDriverClassName("com.wish.commandblockerbungee.libs.sqlite.JDBC");
            File file = new File(plugin.getDataFolder(), "database.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        hikariConfig.setPoolName("CommandBlockerPool");
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTimeout(30000);

        this.dataSource = new HikariDataSource(hikariConfig);
        createTable();
    }

    private void createTable() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS " + tablePrefix + "cooldowns (" +
                     "uuid VARCHAR(36) PRIMARY KEY, " +
                     "attempts INT, " +
                     "last_attempt BIGINT, " +
                     "timeout_until BIGINT)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create database table: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> saveCooldown(UUID uuid, int attempts, long lastAttempt, long timeoutUntil) {
        if (dataSource == null) return CompletableFuture.completedFuture(null);
        
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("REPLACE INTO " + tablePrefix + "cooldowns (uuid, attempts, last_attempt, timeout_until) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, attempts);
                ps.setLong(3, lastAttempt);
                ps.setLong(4, timeoutUntil);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving cooldown data: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<CooldownData> loadCooldown(UUID uuid) {
        if (dataSource == null) return CompletableFuture.completedFuture(null);

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tablePrefix + "cooldowns WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new CooldownData(
                                rs.getInt("attempts"),
                                rs.getLong("last_attempt"),
                                rs.getLong("timeout_until")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading cooldown data: " + e.getMessage());
            }
            return null;
        });
    }

    public void reload() {
        close();
        init();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public static class CooldownData {
        public final int attempts;
        public final long lastAttempt;
        public final long timeoutUntil;

        public CooldownData(int attempts, long lastAttempt, long timeoutUntil) {
            this.attempts = attempts;
            this.lastAttempt = lastAttempt;
            this.timeoutUntil = timeoutUntil;
        }
    }
}
