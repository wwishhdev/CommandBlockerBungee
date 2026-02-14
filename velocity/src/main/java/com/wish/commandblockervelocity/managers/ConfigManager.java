package com.wish.commandblockervelocity.managers;

import com.velocitypowered.api.plugin.PluginContainer;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.utils.NotificationAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ConfigManager {

    private final CommandBlockerVelocity plugin;
    private final Path dataDirectory;
    private ConfigurationNode rootNode;
    private final MiniMessage miniMessage;
    
    // Pre-compile the regex pattern for performance
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile(".*<[#a-zA-Z0-9_]+(:.+?)?>.*");

    public ConfigManager(CommandBlockerVelocity plugin, Path dataDirectory) {
        this.plugin = plugin;
        this.dataDirectory = dataDirectory;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void loadConfiguration() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        plugin.getLogger().error("Could not find config.yml in plugin resources!");
                        return;
                    }
                }
            }

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configPath)
                    .build();
            rootNode = loader.load();
            
        } catch (IOException e) {
            plugin.getLogger().error("Error loading configuration file: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().error("Unexpected error loading configuration: " + e.getMessage());
        }
    }

    // Helper for lists
    private List<String> getStringList(String... path) {
        try {
            return rootNode.node((Object[]) path).getList(String.class, Collections.emptyList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    
    // Helper for strings
    private String getString(String def, String... path) {
        return rootNode.node((Object[]) path).getString(def);
    }

    // Helper for booleans
    private boolean getBoolean(boolean def, String... path) {
        return rootNode.node((Object[]) path).getBoolean(def);
    }
    
    // Helper for ints
    private int getInt(int def, String... path) {
        return rootNode.node((Object[]) path).getInt(def);
    }

    public List<String> getBlockedCommands() {
        return getStringList("blocked-commands");
    }

    public Component getBlockMessage() {
        return parse(getString("<red>This command is blocked.", "messages", "block-message"));
    }

    public String getBypassPermission() {
        return getString("commandblocker.bypass", "bypass-permission");
    }

    // Alias Detection
    public boolean isAliasDetectionEnabled() {
        return getBoolean(true, "alias-detection", "enabled");
    }

    public boolean isBlockHelpSubcommand() {
        return getBoolean(true, "alias-detection", "block-help-subcommand");
    }

    public boolean isBlockPluginPrefix() {
        return getBoolean(true, "alias-detection", "block-plugin-prefix");
    }

    // Allowed Commands
    public boolean isAllowedCommandsEnabled() {
        return getBoolean(true, "allowed-commands-settings", "enabled");
    }

    public List<String> getAllowedCommands() {
        return getStringList("allowed-commands-settings", "commands");
    }

    // Cooldown
    public boolean isCooldownEnabled() {
        return getBoolean(true, "cooldown", "enabled");
    }

    public int getMaxAttempts() {
        return getInt(3, "cooldown", "max-attempts");
    }

    public int getTimeoutDuration() {
        return getInt(300, "cooldown", "timeout-duration");
    }

    public int getResetAfter() {
        return getInt(600, "cooldown", "reset-after");
    }

    public String getTimeoutMessageRaw() {
         return getString("<red>You have exceeded the attempt limit. Wait <yellow>{time} <red>to try again.", "cooldown", "timeout-message");
    }

    // Notifications
    public boolean isNotificationsEnabled() {
        return getBoolean(true, "notifications", "enabled");
    }

    public String getNotifyPermission() {
        return getString("commandblocker.notify", "notifications", "permission");
    }

    public String getNotifyMessageRaw() {
        return getString("<yellow>{player} <gray>tried to use blocked command: <red>{command}", "notifications", "command-message");
    }

    public boolean isNotifyOnTimeout() {
        return getBoolean(true, "notifications", "notify-on-timeout");
    }

    public String getTimeoutNotificationRaw() {
        return getString("<yellow>{player} <gray>has been timed out for exceeding attempts.", "notifications", "timeout-message");
    }

    // Messages
    public Component getReloadSuccessMessage() {
        return parse(getString("<green>CommandBlocker has been reloaded successfully!", "messages", "reload-success"));
    }

    public String getReloadErrorMessageRaw() {
        return getString("<red>Error reloading configuration: {error}", "messages", "reload-error");
    }

    public Component getNoPermissionMessage() {
        return parse(getString("<red>You don't have permission to use this command.", "messages", "no-permission"));
    }

    public String getConsoleReloadMessageRaw() {
        return getString("Configuration reloaded by {player}", "messages", "console-reload");
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
        // Strip legacy color codes (&c, &l)
        return miniMessage.escapeTags(text.replace("&", ""));
    }
    
    // Kept for compatibility if needed, but 'parse' is preferred
    public Component color(String text) {
        return parse(text);
    }

    // ========================================================================
    // Database Settings
    // ========================================================================
    public boolean isDatabaseEnabled() {
        return getBoolean(false, "database", "enabled");
    }

    public String getDatabaseType() {
        return getString("sqlite", "database", "type");
    }

    public String getDatabaseHost() {
        return getString("localhost", "database", "host");
    }

    public int getDatabasePort() {
        return getInt(3306, "database", "port");
    }

    public String getDatabaseUser() {
        return getString("root", "database", "user");
    }

    public String getDatabasePassword() {
        return getString("", "database", "password");
    }

    public String getDatabaseName() {
        return getString("minecraft", "database", "database");
    }

    public String getDatabaseTablePrefix() {
        return getString("cb_", "database", "table-prefix");
    }

    public boolean isDatabaseUseSSL() {
        return getBoolean(false, "database", "use-ssl");
    }

    public boolean isDatabaseAutoReconnect() {
        return getBoolean(true, "database", "auto-reconnect");
    }

    public int getDatabaseMaxPoolSize() {
        return getInt(10, "database", "max-pool-size");
    }

    public int getDatabaseConnectionTimeout() {
        return getInt(30000, "database", "connection-timeout");
    }

    // ========================================================================
    // Discord Webhook
    // ========================================================================
    public boolean isWebhookEnabled() {
        return getBoolean(false, "discord-webhook", "enabled");
    }

    public String getWebhookUrl() {
        return getString("", "discord-webhook", "url");
    }

    public String getWebhookUsername() {
        return getString("CommandBlocker", "discord-webhook", "username");
    }

    public String getWebhookAvatarUrl() {
        return getString("", "discord-webhook", "avatar-url");
    }

    public String getWebhookContent() {
        return getString("**{player}** tried to use blocked command: `{command}`", "discord-webhook", "content");
    }

    // ========================================================================
    // Notification Actions
    // ========================================================================
    public boolean isNotificationActionsEnabled() {
        return getBoolean(false, "notification-actions", "enabled");
    }

    public List<NotificationAction> getNotificationActions() {
        List<NotificationAction> actions = new ArrayList<>();
        try {
            List<? extends ConfigurationNode> list = rootNode.node("notification-actions", "actions").childrenList();
            for (ConfigurationNode node : list) {
                String label = node.node("label").getString();
                String hover = node.node("hover").getString();
                String command = node.node("command").getString();
                if (label != null && command != null) {
                    actions.add(new NotificationAction(label, hover, command));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error loading notification actions: " + e.getMessage());
        }
        return actions;
    }

    // ========================================================================
    // Granular Permissions
    // ========================================================================
    public String getBypassAllPermission() {
        return getString("commandblocker.bypass", "permissions", "bypass-all");
    }

    public String getBypassBlockPermission() {
        return getString("commandblocker.bypass.block", "permissions", "bypass-block");
    }

    public String getBypassCooldownPermission() {
        return getString("commandblocker.bypass.cooldown", "permissions", "bypass-cooldown");
    }
}