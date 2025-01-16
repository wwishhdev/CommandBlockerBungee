package com.wish.commandblockerbungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.bstats.bungeecord.Metrics;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandBlockerBungee extends Plugin implements Listener {
    private Configuration configuration;
    private List<String> blockedCommands;
    private String blockMessage;
    private String bypassPermission;
    private List<String> allowedCommands;
    private boolean allowedCommandsEnabled;

    // Variables para la detección de aliases
    private boolean aliasDetectionEnabled;
    private boolean blockHelpSubcommand;
    private boolean blockPluginPrefix;
    private boolean blockShortVersion;

    // Variables para el sistema de cooldown y notificaciones
    private Map<UUID, CommandAttempts> playerAttempts;
    private boolean cooldownEnabled;
    private int maxAttempts;
    private int timeoutDuration;
    private int resetAfter;
    private String timeoutMessage;
    private boolean notificationsEnabled;
    private String notifyPermission;
    private String notifyMessage;
    private boolean notifyOnTimeout;
    private String timeoutNotification;

    private String reloadSuccessMessage;
    private String reloadErrorMessage;
    private String noPermissionMessage;
    private String consoleReloadMessage;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        // Initialize bStats
        int pluginId = 24030;
        Metrics metrics = new Metrics(this, pluginId);

        loadConfiguration();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand());
        getProxy().getConsole().sendMessage(ChatColor.GOLD + "\n" +
                " ██████╗ ██████╗ ███╗   ███╗███╗   ███╗ █████╗ ███╗   ██╗██████╗ ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗██████╗ \n" +
                "██╔════╝██╔═══██╗████╗ ████║████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗\n" +
                "██║     ██║   ██║██╔████╔██║██╔████╔██║███████║██╔██╗ ██║██║  ██║██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗  ██████╔╝\n" +
                "██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║  ██║██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗\n" +
                "╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║  ██║██║ ╚████║██████╔╝██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗██║  ██║\n" +
                " ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝\n" +
                ChatColor.YELLOW + "                Thanks for using CommandBlockerBungee! " + ChatColor.RED + "❤\n" +
                ChatColor.AQUA + "                                                          by wwishh\n" +
                ChatColor.GREEN + "        If you like the plugin, please leave a review at: " +
                ChatColor.GOLD + "https://www.spigotmc.org/resources/❌-commandblockerbungee-1-8-1-21.120890/\n");

        getLogger().info("CommandBlockerBungee has been enabled! by wwishh <3");
        playerAttempts = new HashMap<>();

        // Programar limpieza periódica de intentos antiguos (cada 30 minutos)
        getProxy().getScheduler().schedule(this, this::cleanupOldAttempts, 30, 30, TimeUnit.MINUTES);
    }

    private void loadConfiguration() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        getLogger().severe("No se pudo encontrar el archivo config.yml en los recursos del plugin!");
                        return;
                    }
                }
            }

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(configFile);
            if (configuration == null) {
                getLogger().severe("Error al cargar la configuración!");
                return;
            }

            loadConfig();
        } catch (IOException e) {
            getLogger().severe("Error al cargar el archivo de configuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        blockedCommands = configuration.getStringList("blocked-commands");
        blockMessage = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("messages.block-message", "&cThis command is blocked."));
        bypassPermission = configuration.getString("bypass-permission", "commandblocker.bypass");

        // Cargar configuración de detección de aliases
        aliasDetectionEnabled = configuration.getBoolean("alias-detection.enabled", true);
        blockHelpSubcommand = configuration.getBoolean("alias-detection.block-help-subcommand", true);
        blockPluginPrefix = configuration.getBoolean("alias-detection.block-plugin-prefix", true);
        blockShortVersion = configuration.getBoolean("alias-detection.block-short-version", true);

        allowedCommandsEnabled = configuration.getBoolean("allowed-commands-settings.enabled", true);
        allowedCommands = configuration.getStringList("allowed-commands-settings.commands");

        // Nuevos mensajes
        reloadSuccessMessage = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("messages.reload-success", "&aCommandBlocker has been reloaded successfully!"));
        reloadErrorMessage = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("messages.reload-error", "&cError reloading configuration: {error}"));
        noPermissionMessage = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("messages.no-permission", "&cYou don't have permission to use this command."));
        consoleReloadMessage = configuration.getString("messages.console-reload", "Configuration reloaded by {player}");

        // Cargar configuración de cooldown
        cooldownEnabled = configuration.getBoolean("cooldown.enabled", true);
        maxAttempts = configuration.getInt("cooldown.max-attempts", 3);
        timeoutDuration = configuration.getInt("cooldown.timeout-duration", 300);
        resetAfter = configuration.getInt("cooldown.reset-after", 600);
        timeoutMessage = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("cooldown.timeout-message", "&cYou have exceeded the attempt limit. Wait &e{time} &cto try again."));

        // Cargar configuración de notificaciones
        notificationsEnabled = configuration.getBoolean("notifications.enabled", true);
        notifyPermission = configuration.getString("notifications.permission", "commandblocker.notify");
        notifyMessage = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("notifications.command-message", "&e{player} &7tried to use blocked command: &c{command}"));
        notifyOnTimeout = configuration.getBoolean("notifications.notify-on-timeout", true);
        timeoutNotification = ChatColor.translateAlternateColorCodes('&',
                configuration.getString("notifications.timeout-message", "&e{player} &7has been timed out for exceeding attempts."));
    }

    private boolean isCommandBlocked(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        try {
            // Remover el slash inicial si existe
            String cleanCommand = command.toLowerCase();
            if (cleanCommand.startsWith("/")) {
                cleanCommand = cleanCommand.substring(1);
            }

            // Obtener el comando base (sin argumentos)
            String[] parts = cleanCommand.split(" ", 2);
            String baseCommand = parts[0];

            if (baseCommand.isEmpty()) {
                return false;
            }

            // Verificar si el comando está en la lista de permitidos
            if (allowedCommandsEnabled) {
                for (String allowedCmd : allowedCommands) {
                    if (allowedCmd == null) continue;

                    allowedCmd = allowedCmd.toLowerCase();
                    // Verificar comando exacto
                    if (baseCommand.equals(allowedCmd)) {
                        return false;
                    }

                    // Verificar si el comando comienza con el comando permitido
                    if (cleanCommand.startsWith(allowedCmd + " ")) {
                        return false;
                    }
                }
            }

            if (!aliasDetectionEnabled) {
                return blockedCommands.contains(baseCommand);
            }

            for (String blockedCmd : blockedCommands) {
                if (blockedCmd == null) continue;

                blockedCmd = blockedCmd.toLowerCase();

                // Verificar comando exacto
                if (baseCommand.equals(blockedCmd)) {
                    return true;
                }

                // Verificar prefijo de plugin si está habilitado
                if (blockPluginPrefix && baseCommand.startsWith(blockedCmd + ":")) {
                    return true;
                }

                // Verificar aliases
                if (isAlias(baseCommand, blockedCmd)) {
                    return true;
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error al procesar comando para bloqueo: " + e.getMessage());
        }

        return false;
    }

    private boolean isAlias(String command, String blockedCmd) {
        List<String> patterns = new ArrayList<>();
        patterns.add(blockedCmd); // Comando exacto

        if (blockShortVersion && blockedCmd.length() > 3) {
            // Primeras 3 letras (solo si el comando tiene más de 3 letras)
            patterns.add(blockedCmd.substring(0, 3));
        }

        for (String pattern : patterns) {
            if (command.equals(pattern)) {
                return true;
            }

            if (blockHelpSubcommand && (
                    command.equals(pattern + "help") ||
                            command.startsWith(pattern + " help"))) {
                return true;
            }
        }

        return false;
    }

    private static class CommandAttempts {
        private int attempts;
        private long lastAttempt;
        private long timeoutUntil;

        public CommandAttempts() {
            this.attempts = 0;
            this.lastAttempt = System.currentTimeMillis();
            this.timeoutUntil = 0;
        }

        public boolean isTimedOut() {
            return System.currentTimeMillis() < timeoutUntil;
        }

        public long getRemainingTimeout() {
            return Math.max(0, timeoutUntil - System.currentTimeMillis()) / 1000;
        }
    }

    private void notifyStaff(String message) {
        if (!notificationsEnabled || message == null) return;

        try {
            for (ProxiedPlayer player : getProxy().getPlayers()) {
                if (player != null && player.isConnected() && player.hasPermission(notifyPermission)) {
                    player.sendMessage(new TextComponent(message));
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error al enviar notificación al staff: " + e.getMessage());
        }
    }

    private boolean handleCooldown(ProxiedPlayer player, String command) {
        if (!cooldownEnabled || player == null) return false;

        try {
            UUID uuid = player.getUniqueId();
            CommandAttempts attempts = playerAttempts.computeIfAbsent(uuid, k -> new CommandAttempts());

            // Verificar si está en timeout
            if (attempts.isTimedOut()) {
                try {
                    String timeLeft = String.valueOf(attempts.getRemainingTimeout());
                    player.sendMessage(new TextComponent(
                            timeoutMessage.replace("{time}", timeLeft + "s")
                    ));
                } catch (Exception e) {
                    getLogger().warning("Error al enviar mensaje de timeout: " + e.getMessage());
                }
                return true;
            }

            // Verificar si los intentos deben resetearse
            long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;
            if (timeSinceLastAttempt > resetAfter) {
                attempts.attempts = 0;
            }

            // Incrementar intentos
            attempts.attempts++;
            attempts.lastAttempt = System.currentTimeMillis();

            // Verificar si debe recibir timeout
            if (attempts.attempts >= maxAttempts) {
                attempts.timeoutUntil = System.currentTimeMillis() + (timeoutDuration * 1000L);

                try {
                    String timeLeft = String.valueOf(timeoutDuration);
                    player.sendMessage(new TextComponent(
                            timeoutMessage.replace("{time}", timeLeft + "s")
                    ));

                    if (notifyOnTimeout) {
                        notifyStaff(timeoutNotification.replace("{player}", player.getName()));
                    }
                } catch (Exception e) {
                    getLogger().warning("Error al procesar timeout: " + e.getMessage());
                }

                return true;
            }

            return false;
        } catch (Exception e) {
            getLogger().severe("Error crítico en el sistema de cooldown: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (!event.isCommand()) return;

        String fullCommand = event.getMessage();

        // Verificar si el remitente es un jugador
        if (event.getSender() instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) event.getSender();

            if (player.hasPermission(bypassPermission)) {
                return;
            }

            // Verificar si el comando está bloqueado
            if (isCommandBlocked(fullCommand)) {
                // Manejar cooldown
                if (handleCooldown(player, fullCommand)) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);
                player.sendMessage(new TextComponent(blockMessage));

                // Notificar al staff
                if (notificationsEnabled) {
                    notifyStaff(notifyMessage
                            .replace("{player}", player.getName())
                            .replace("{command}", fullCommand));
                }
            }
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();

        if (player.hasPermission(bypassPermission)) {
            return;
        }

        String cursor = event.getCursor().toLowerCase();

        // Verificar si el comando que se está intentando autocompletar está bloqueado
        if (isCommandBlocked(cursor)) {
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    private class ReloadCommand extends Command {
        public ReloadCommand() {
            super("cblockerreload", "commandblocker.reload", "cbreload");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender == null) return;

            if (sender.hasPermission("commandblocker.reload")) {
                try {
                    loadConfiguration();
                    sender.sendMessage(new TextComponent(reloadSuccessMessage));
                    getLogger().info(consoleReloadMessage.replace("{player}", sender.getName()));
                } catch (Exception e) {
                    sender.sendMessage(new TextComponent(
                            reloadErrorMessage.replace("{error}", e.getMessage())
                    ));
                    getLogger().severe("Error al recargar la configuración: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                sender.sendMessage(new TextComponent(noPermissionMessage));
            }
        }
    }

    private void cleanupOldAttempts() {
        if (playerAttempts == null) return;

        try {
            long currentTime = System.currentTimeMillis();
            playerAttempts.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue().lastAttempt) / 1000 > resetAfter * 2);
        } catch (Exception e) {
            getLogger().warning("Error al limpiar intentos antiguos: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Limpiar el mapa de intentos para liberar memoria
        if (playerAttempts != null) {
            playerAttempts.clear();
            playerAttempts = null;
        }

        // Limpiar otras referencias
        blockedCommands = null;
        configuration = null;

        getLogger().info("CommandBlockerBungee has been disabled! by wwishh <3");
    }
}