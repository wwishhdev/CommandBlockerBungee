package com.wish.commandblockerbungee.managers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.utils.NotificationAction;
import com.wish.commandblockerbungee.utils.PunishmentAction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class ConfigManager {

    private final CommandBlockerBungee plugin;
    private Configuration configuration;
    private final MiniMessage miniMessage;
    
    // Pre-compile the regex pattern for performance (DOTALL so '.' matches newlines too)
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile(".*<[#a-zA-Z0-9_]+(:.+?)?>.*", Pattern.DOTALL);

    public ConfigManager(CommandBlockerBungee plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void loadConfiguration() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdir();
            }

            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        plugin.getLogger().severe("Could not find config.yml in plugin resources!");
                        return;
                    }
                }
            }

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            if (configuration == null) {
                plugin.getLogger().severe("Error loading configuration!");
                return;
            }
            validateConfiguration();
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading configuration file: " + e.getMessage());
        }
    }

    /**
     * NEW: Validates config values and logs warnings for invalid or inconsistent settings.
     */
    public void validateConfiguration() {
        if (getMaxAttempts() <= 0) {
            plugin.getLogger().warning("[Config] cooldown.max-attempts must be > 0. Got: " + getMaxAttempts() + ". Defaulting behaviour may be unexpected.");
        }
        if (getTimeoutDuration() <= 0) {
            plugin.getLogger().warning("[Config] cooldown.timeout-duration must be > 0. Got: " + getTimeoutDuration() + ".");
        }
        if (getResetAfter() <= 0) {
            plugin.getLogger().warning("[Config] cooldown.reset-after must be > 0. Got: " + getResetAfter() + ".");
        }
        if (getResetAfter() <= getTimeoutDuration()) {
            plugin.getLogger().warning("[Config] cooldown.reset-after (" + getResetAfter() + "s) should be greater than " +
                    "cooldown.timeout-duration (" + getTimeoutDuration() + "s) to avoid immediately resetting timeouts.");
        }
        if (isWebhookEnabled() && getWebhookUrl().isEmpty()) {
            plugin.getLogger().warning("[Config] discord-webhook is enabled but no URL is set.");
        }
        if (isDatabaseEnabled()) {
            if (getDatabaseType().equalsIgnoreCase("mysql")) {
                if (getDatabaseHost().isEmpty()) {
                    plugin.getLogger().warning("[Config] database.host is empty. MySQL will likely fail to connect.");
                }
                if (getDatabasePassword().isEmpty()) {
                    plugin.getLogger().warning("[Config] database.password is empty. This is insecure for MySQL.");
                }
            }
        }
        if (getBlockedCommands().isEmpty()) {
            plugin.getLogger().warning("[Config] blocked-commands list is empty. No commands will be blocked.");
        }
    }

    public List<String> getBlockedCommands() {
        return configuration.getStringList("blocked-commands");
    }

    public Component getBlockMessage() {
        return parse(configuration.getString("messages.block-message", "<red>This command is blocked."));
    }

    public boolean isCustomMessagesEnabled() {
        return configuration.getBoolean("custom-messages.enabled", false);
    }

    public Component getCustomBlockMessage(String baseCommand) {
        if (!isCustomMessagesEnabled()) return null;
        String msg = configuration.getString("custom-messages.commands." + baseCommand.toLowerCase(), null);
        return msg != null ? parse(msg) : null;
    }

    public boolean isAliasDetectionEnabled() {
        return configuration.getBoolean("alias-detection.enabled", true);
    }

    public boolean isBlockHelpSubcommand() {
        return configuration.getBoolean("alias-detection.block-help-subcommand", true);
    }

    public boolean isBlockPluginPrefix() {
        return configuration.getBoolean("alias-detection.block-plugin-prefix", true);
    }

    public boolean isAllowedCommandsEnabled() {
        return configuration.getBoolean("allowed-commands-settings.enabled", true);
    }

    public List<String> getAllowedCommands() {
        return configuration.getStringList("allowed-commands-settings.commands");
    }

    // ========================================================================
    // Server-specific Blocked Commands
    // ========================================================================

    /**
     * NEW: Returns the list of commands blocked only on a specific server.
     * Returns an empty list if no server-specific rules exist for that server.
     */
    public List<String> getServerBlockedCommands(String serverName) {
        if (serverName == null || serverName.isEmpty()) return Collections.emptyList();
        try {
            return configuration.getStringList("server-blocked-commands." + serverName.toLowerCase());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns all server-specific blocked command mappings as a map.
     */
    public Map<String, List<String>> getAllServerBlockedCommands() {
        Map<String, List<String>> result = new HashMap<>();
        net.md_5.bungee.config.Configuration section = configuration.getSection("server-blocked-commands");
        if (section == null) return result;
        for (String key : section.getKeys()) {
            result.put(key, configuration.getStringList("server-blocked-commands." + key));
        }
        return result;
    }

    public boolean isCooldownEnabled() {
        return configuration.getBoolean("cooldown.enabled", true);
    }

    public int getMaxAttempts() {
        return configuration.getInt("cooldown.max-attempts", 3);
    }

    public int getTimeoutDuration() {
        return configuration.getInt("cooldown.timeout-duration", 300);
    }

    public int getResetAfter() {
        return configuration.getInt("cooldown.reset-after", 600);
    }

    public String getTimeoutMessageRaw() {
        return configuration.getString("cooldown.timeout-message", "<red>You have exceeded the attempt limit. Wait <yellow>{time} <red>to try again.");
    }

    public boolean isNotificationsEnabled() {
        return configuration.getBoolean("notifications.enabled", true);
    }

    public String getNotifyPermission() {
        return configuration.getString("permissions.notify", "commandblocker.notify");
    }

    public String getNotifyMessageRaw() {
        return configuration.getString("notifications.command-message", "<yellow>{player} <gray>tried to use blocked command: <red>{command}");
    }

    public boolean isNotifyOnTimeout() {
        return configuration.getBoolean("notifications.notify-on-timeout", true);
    }

    public String getTimeoutNotificationRaw() {
        return configuration.getString("notifications.timeout-message", "<yellow>{player} <gray>has been timed out for exceeding attempts.");
    }

    public Component getReloadSuccessMessage() {
        return parse(configuration.getString("messages.reload-success", "<green>CommandBlocker has been reloaded successfully!"));
    }

    public String getReloadErrorMessageRaw() {
        return configuration.getString("messages.reload-error", "<red>Error reloading configuration: {error}");
    }

    public Component getNoPermissionMessage() {
        return parse(configuration.getString("messages.no-permission", "<red>You don't have permission to use this command."));
    }

    public String getConsoleReloadMessage() {
        return configuration.getString("messages.console-reload", "Configuration reloaded by {player}");
    }

    public Component parse(String text) {
        if (text == null) return Component.empty();
        
        // Smarter detection using pre-compiled pattern
        if (MINIMESSAGE_PATTERN.matcher(text).matches()) {
            return miniMessage.deserialize(text);
        }
        
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public String escape(String text) {
        if (text == null) return "";
        // Strip only valid legacy color codes (&c, &l, etc)
        String clean = text.replaceAll("(?i)&[0-9a-fk-or]", "");
        return miniMessage.escapeTags(clean);
    }

    // ========================================================================
    // Database Settings
    // ========================================================================
    public boolean isDatabaseEnabled() {
        return configuration.getBoolean("database.enabled", false);
    }

    public String getDatabaseType() {
        return configuration.getString("database.type", "sqlite");
    }

    public String getDatabaseHost() {
        return configuration.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return configuration.getInt("database.port", 3306);
    }

    public String getDatabaseUser() {
        return configuration.getString("database.user", "root");
    }

    public String getDatabasePassword() {
        return configuration.getString("database.password", "");
    }

    public String getDatabaseName() {
        return configuration.getString("database.database", "minecraft");
    }

    public String getDatabaseTablePrefix() {
        return configuration.getString("database.table-prefix", "cb_");
    }

    public boolean isDatabaseUseSSL() {
        return configuration.getBoolean("database.use-ssl", false);
    }

    public boolean isDatabaseAutoReconnect() {
        return configuration.getBoolean("database.auto-reconnect", true);
    }
    
    public int getDatabaseMaxPoolSize() {
        int size = configuration.getInt("database.max-pool-size", 10);
        return Math.max(1, Math.min(size, 50));
    }
    
    public int getDatabaseConnectionTimeout() {
        int timeout = configuration.getInt("database.connection-timeout", 30000);
        return Math.max(1000, Math.min(timeout, 120000));
    }
    
    public int getThreadPoolSize() {
        int size = configuration.getInt("database.thread-pool-size", 4);
        return Math.max(1, Math.min(size, 32));
    }

    // ========================================================================
    // Discord Webhook
    // ========================================================================
    public boolean isWebhookEnabled() {
        return configuration.getBoolean("discord-webhook.enabled", false);
    }

    public String getWebhookUrl() {
        return configuration.getString("discord-webhook.url", "");
    }

    public String getWebhookUsername() {
        return configuration.getString("discord-webhook.username", "CommandBlocker");
    }

    public String getWebhookAvatarUrl() {
        return configuration.getString("discord-webhook.avatar-url", "");
    }

    public String getWebhookContent() {
        return configuration.getString("discord-webhook.content", "**{player}** tried to use blocked command: `{command}`");
    }

    public int getWebhookRateLimit() {
        int limit = configuration.getInt("discord-webhook.rate-limit", 10);
        return Math.max(1, Math.min(limit, 300));
    }

    // ========================================================================
    // Notification Actions
    // ========================================================================
    public boolean isNotificationActionsEnabled() {
        return configuration.getBoolean("notification-actions.enabled", false);
    }

    public List<NotificationAction> getNotificationActions() {
        List<NotificationAction> actions = new ArrayList<>();
        List<?> list = configuration.getList("notification-actions.actions");
        
        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    String label = (String) map.get("label");
                    String hover = (String) map.get("hover");
                    String command = (String) map.get("command");
                    if (label != null && command != null) {
                        actions.add(new NotificationAction(label, hover, command));
                    }
                }
            }
        }
        return actions;
    }

    // ========================================================================
    // Auto-Punishments
    // ========================================================================
    public boolean isAutoPunishmentsEnabled() {
        return configuration.getBoolean("auto-punishments.enabled", false);
    }

    public List<PunishmentAction> getAutoPunishments() {
        List<PunishmentAction> actions = new ArrayList<>();
        List<?> list = configuration.getList("auto-punishments.actions");

        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    Object thresholdObj = map.get("threshold");
                    String command = (String) map.get("command");
                    if (thresholdObj instanceof Number && command != null) {
                        actions.add(new PunishmentAction(((Number) thresholdObj).intValue(), command));
                    }
                }
            }
        }
        actions.sort((a, b) -> Integer.compare(a.getThreshold(), b.getThreshold()));
        return actions;
    }

    // ========================================================================
    // Audit Log
    // ========================================================================

    public boolean isAuditLogEnabled() {
        return configuration.getBoolean("audit-log.enabled", false);
    }

    public int getAuditLogMaxFiles() {
        int max = configuration.getInt("audit-log.max-files", 30);
        return Math.max(1, Math.min(max, 365));
    }

    // ========================================================================
    // Tab-Complete Whitelist
    // ========================================================================
    public boolean isTabCompleteWhitelistEnabled() {
        return configuration.getBoolean("tab-complete-whitelist.enabled", false);
    }

    public List<String> getTabCompleteWhitelistAllowed() {
        return configuration.getStringList("tab-complete-whitelist.allowed");
    }

    // ========================================================================
    // Granular Permissions
    // ========================================================================
    public String getBypassAllPermission() {
        return configuration.getString("permissions.bypass-all", "commandblocker.bypass");
    }

    public String getBypassBlockPermission() {
        return configuration.getString("permissions.bypass-block", "commandblocker.bypass.block");
    }

    public String getBypassCooldownPermission() {
        return configuration.getString("permissions.bypass-cooldown", "commandblocker.bypass.cooldown");
    }
}