package com.wish.commandblockerbungee;

import com.wish.commandblockerbungee.commands.ReloadCommand;
import com.wish.commandblockerbungee.database.DatabaseManager;
import com.wish.commandblockerbungee.listeners.ChatListener;
import com.wish.commandblockerbungee.listeners.ConnectionListener;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import com.wish.commandblockerbungee.managers.WebhookManager;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

public class CommandBlockerBungee extends Plugin {

    private ConfigManager configManager;
    private CooldownManager cooldownManager;
    private DatabaseManager databaseManager;
    private WebhookManager webhookManager;
    private BungeeAudiences adventure;

    @Override
    public void onEnable() {
        // Initialize Adventure
        this.adventure = BungeeAudiences.create(this);

        // ASCII Art
        getProxy().getConsole().sendMessage(new net.md_5.bungee.api.chat.TextComponent(
                ChatColor.GOLD + "\n" +
                " ██████╗ ██████╗ ███╗   ███╗███╗   ███╗ █████╗ ███╗   ██╗██████╗ ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗██████╗ \n" +
                "██╔════╝██╔═══██╗████╗ ████║████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗\n" +
                "██║     ██║   ██║██╔████╔██║██╔████╔██║███████║██╔██╗ ██║██║  ██║██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗  ██████╔╝\n" +
                "██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║  ██║██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗\n" +
                "╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║  ██║██║ ╚████║██████╔╝██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗██║  ██║\n" +
                " ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝\n" +
                ChatColor.YELLOW + "                CommandBlockerBungee v2.1.2 " + ChatColor.RED + "❤\n" +
                ChatColor.AQUA + "                                                          by wwishhdev\n"
        ));

        // Managers
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfiguration();
        
        this.databaseManager = new DatabaseManager(this, configManager);
        this.databaseManager.init();
        
        this.webhookManager = new WebhookManager(this, configManager);

        this.cooldownManager = new CooldownManager(this, configManager, databaseManager);

        // Listeners & Commands
        getProxy().getPluginManager().registerListener(this, new ChatListener(this, configManager, cooldownManager, webhookManager));
        getProxy().getPluginManager().registerListener(this, new ConnectionListener(cooldownManager));
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        // bStats
        new Metrics(this, 24030);

        getLogger().info("CommandBlockerBungee has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        if (cooldownManager != null) {
            cooldownManager.clear();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("CommandBlockerBungee has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public BungeeAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Cannot retrieve audience provider while plugin is not enabled");
        }
        return this.adventure;
    }
}