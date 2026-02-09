package com.wish.commandblockerbungee.managers;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import java.util.regex.Pattern;

public class ConfigManager {

    private final CommandBlockerBungee plugin;
    private Configuration configuration;
    private final MiniMessage miniMessage;
    
    // Pre-compile the regex pattern for performance
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile(".*<[#a-zA-Z0-9_]+(:.+?)?>.*");

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
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading configuration file: " + e.getMessage());
        }
    }

    public List<String> getBlockedCommands() {
        return configuration.getStringList("blocked-commands");
    }

    public Component getBlockMessage() {
        return parse(configuration.getString("messages.block-message", "<red>This command is blocked."));
    }

    public String getBypassPermission() {
        return configuration.getString("bypass-permission", "commandblocker.bypass");
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
        return configuration.getString("notifications.permission", "commandblocker.notify");
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
        return miniMessage.escapeTags(text);
    }
}