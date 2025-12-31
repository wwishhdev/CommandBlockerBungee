package com.wish.commandblockerbungee;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ConfigManager {
    private final CommandBlockerBungee plugin;
    private Configuration configuration;
    private List<Pattern> blockedRegexPatterns;

    public ConfigManager(CommandBlockerBungee plugin) {
        this.plugin = plugin;
        this.blockedRegexPatterns = new ArrayList<>();
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

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(configFile);
            if (configuration == null) {
                plugin.getLogger().severe("Error loading configuration!");
            } else {
                loadRegexPatterns();
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Error loading configuration file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadRegexPatterns() {
        blockedRegexPatterns.clear();
        List<String> regexList = configuration.getStringList("blocked-regex");
        if (regexList != null) {
            for (String regex : regexList) {
                try {
                    blockedRegexPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid regex pattern in config: " + regex);
                }
            }
        }
    }

    public List<Pattern> getBlockedRegexPatterns() {
        return blockedRegexPatterns;
    }

    public Configuration getConfig() {
        return configuration;
    }

    public String getString(String path, String def) {
        return configuration.getString(path, def);
    }

    public List<String> getStringList(String path) {
        return configuration.getStringList(path);
    }

    public boolean getBoolean(String path, boolean def) {
        return configuration.getBoolean(path, def);
    }

    public int getInt(String path, int def) {
        return configuration.getInt(path, def);
    }
}
