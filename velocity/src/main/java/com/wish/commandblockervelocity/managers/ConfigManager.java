package com.wish.commandblockervelocity.managers;

import com.velocitypowered.api.plugin.PluginContainer;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
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
import java.util.Collections;
import java.util.List;

public class ConfigManager {

    private final CommandBlockerVelocity plugin;
    private final Path dataDirectory;
    private ConfigurationNode rootNode;
    private final MiniMessage miniMessage;

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
        
        // Smarter detection: 
        // If it contains MiniMessage tags (<tag> or <#hex>), use MiniMessage.
        // Otherwise, fallback to Legacy Ampersand.
        // This regex looks for: < + (alpha-numeric OR #hex) + (optional parameters) + >
        // Examples: <red>, <#ff0000>, <gradient:red:blue>
        if (text.matches(".*<[#a-zA-Z0-9_]+(:.+?)?>.*")) {
            return miniMessage.deserialize(text);
        }
        
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
    
    // Kept for compatibility if needed, but 'parse' is preferred
    public Component color(String text) {
        return parse(text);
    }
}