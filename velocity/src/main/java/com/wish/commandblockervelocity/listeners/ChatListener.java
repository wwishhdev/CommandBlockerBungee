package com.wish.commandblockervelocity.listeners;

import java.util.Locale;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;
import com.wish.commandblockervelocity.managers.WebhookManager;
import com.wish.commandblockervelocity.utils.CommandMatcher;
import com.wish.commandblockervelocity.utils.FileLogger;
import com.wish.commandblockervelocity.utils.NotificationAction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ChatListener {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;
    private final WebhookManager webhookManager;
    private final FileLogger fileLogger;
    private final CommandMatcher commandMatcher;

    public ChatListener(CommandBlockerVelocity plugin, ConfigManager config, CooldownManager cooldownManager,
                        WebhookManager webhookManager, FileLogger fileLogger) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.webhookManager = webhookManager;
        this.fileLogger = fileLogger;
        this.commandMatcher = new CommandMatcher(new CommandMatcher.Rules() {
            @Override
            public boolean isAllowedCommandsEnabled() {
                return config.isAllowedCommandsEnabled();
            }

            @Override
            public List<String> getAllowedCommands() {
                return config.getAllowedCommands();
            }

            @Override
            public List<String> getBlockedCommands() {
                return config.getBlockedCommands();
            }

            @Override
            public List<String> getServerBlockedCommands(String serverName) {
                return config.getServerBlockedCommands(serverName);
            }

            @Override
            public boolean isAliasDetectionEnabled() {
                return config.isAliasDetectionEnabled();
            }

            @Override
            public boolean isBlockPluginPrefix() {
                return config.isBlockPluginPrefix();
            }

            @Override
            public boolean isBlockHelpSubcommand() {
                return config.isBlockHelpSubcommand();
            }

            @Override
            public void warn(String message) {
                plugin.getLogger().warn(message);
            }
        });
    }

    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String fullCommand = event.getCommand();
        String serverName = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        if (commandMatcher.isCommandBlocked(fullCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;

            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;

            if (!bypassCooldown) {
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setResult(CommandExecuteEvent.CommandResult.denied());

            if (!onCooldown) {
                String baseCmd = commandMatcher.getBaseCommandForMessage(fullCommand);
                Component customMsg = config.getCustomBlockMessage(baseCmd);
                player.sendMessage(customMsg != null ? customMsg : config.getBlockMessage());
            }

            if (config.isAuditLogEnabled()) {
                fileLogger.logBlockedCommand(player.getUsername(), player.getUniqueId().toString(), serverName, fullCommand);
            }

            if (config.isDatabaseEnabled()) {
                plugin.getDatabaseManager().logBlockedCommand(
                        player.getUniqueId().toString(), player.getUsername(), serverName, fullCommand);
            }

            webhookManager.sendWebhook(player.getUsername(), fullCommand, serverName, player.getUniqueId().toString());

            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand, serverName);
            }
        }
    }

    public void onTabComplete(TabCompleteEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String serverName = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");
        boolean bypassBlock = player.hasPermission(config.getBypassBlockPermission());
        boolean whitelistEnabled = config.isTabCompleteWhitelistEnabled();
        List<String> whitelistAllowed = config.getTabCompleteWhitelistAllowed();
        List<String> playerSuggestionCommands = config.getTabCompletePlayerSuggestionCommands();

        event.getSuggestions().removeIf(suggestion ->
                commandMatcher.shouldHideTabSuggestion(suggestion, serverName, whitelistEnabled, whitelistAllowed)
                        && !(bypassBlock && !whitelistEnabled));

        if (whitelistEnabled) {
            addWhitelistedSuggestions(player, event.getSuggestions(), event.getPartialMessage(), whitelistAllowed, playerSuggestionCommands);
        }

        if (!whitelistEnabled && !bypassBlock
                && commandMatcher.isCommandBlocked(event.getPartialMessage(), serverName)) {
            event.getSuggestions().clear();
        }
    }

    public void onPlayerAvailableCommands(PlayerAvailableCommandsEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String serverName = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");
        boolean bypassBlock = player.hasPermission(config.getBypassBlockPermission());
        boolean whitelistEnabled = config.isTabCompleteWhitelistEnabled();
        List<String> whitelistAllowed = config.getTabCompleteWhitelistAllowed();
        List<String> playerSuggestionCommands = config.getTabCompletePlayerSuggestionCommands();

        event.getRootNode().getChildren().removeIf((CommandNode<?> node) ->
                commandMatcher.shouldHideTabSuggestion(node.getName(), serverName, whitelistEnabled, whitelistAllowed)
                        && !(bypassBlock && !whitelistEnabled));

        if (whitelistEnabled) {
            addWhitelistedRootCommands(event.getRootNode(), whitelistAllowed, playerSuggestionCommands);
        }
    }

    private void addWhitelistedSuggestions(Player player, List<String> suggestions, String partialMessage,
                                           List<String> whitelistAllowed, List<String> playerSuggestionCommands) {
        String rootCommand = getRootCommand(partialMessage);
        if (isPlayerSuggestionCommand(rootCommand, playerSuggestionCommands) && partialMessage.trim().contains(" ")) {
            addOnlinePlayerSuggestions(player, suggestions, getFirstArgumentPrefix(partialMessage));
            return;
        }

        String partialRoot = normalizeRootCommand(partialMessage);
        for (String allowed : whitelistAllowed) {
            String command = normalizeRootCommand(allowed);
            if (command.isEmpty() || (!partialRoot.isEmpty() && !command.startsWith(partialRoot))) {
                continue;
            }
            if (suggestions.stream().noneMatch(existing -> normalizeRootCommand(existing).equals(command))) {
                suggestions.add(command);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addWhitelistedRootCommands(RootCommandNode<?> rootNode, List<String> whitelistAllowed, List<String> playerSuggestionCommands) {
        RootCommandNode rawRoot = rootNode;
        for (String allowed : whitelistAllowed) {
            String command = normalizeRootCommand(allowed);
            if (command.isEmpty()) {
                continue;
            }

            CommandNode<?> existing = rootNode.getChild(command);
            CommandNode commandNode = existing != null
                    ? existing
                    : LiteralArgumentBuilder.literal(command).build();

            if (isPlayerSuggestionCommand(command, playerSuggestionCommands)
                    && commandNode.getChild("player") == null) {
                RequiredArgumentBuilder playerArgument = RequiredArgumentBuilder.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            addMatchingOnlinePlayers(builder);
                            return builder.buildFuture();
                        });
                playerArgument.then(RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString()));
                commandNode.addChild(playerArgument.build());
            }

            if (existing == null) {
                rawRoot.addChild(commandNode);
            }
        }
    }

    private void addMatchingOnlinePlayers(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        plugin.getProxy().getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
    }

    private void addOnlinePlayerSuggestions(Player player, List<String> suggestions, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        plugin.getProxy().getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> !name.equalsIgnoreCase(player.getUsername()))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .filter(name -> suggestions.stream().noneMatch(existing -> existing.equalsIgnoreCase(name)))
                .forEach(suggestions::add);
    }

    private boolean isPlayerSuggestionCommand(String command, List<String> playerSuggestionCommands) {
        String normalized = normalizeRootCommand(command);
        if (normalized.isEmpty()) return false;

        return playerSuggestionCommands.stream()
                .map(this::normalizeRootCommand)
                .anyMatch(normalized::equals);
    }

    private String getRootCommand(String partialMessage) {
        return normalizeRootCommand(partialMessage);
    }

    private String getFirstArgumentPrefix(String partialMessage) {
        if (partialMessage == null) return "";

        String normalized = partialMessage.trim();
        String[] parts = normalized.split("(?U)\\s+", 3);
        return parts.length >= 2 ? parts[1] : "";
    }

    private String normalizeRootCommand(String command) {
        if (command == null) return "";

        String normalized = command.trim().toLowerCase(Locale.ROOT).replaceAll("^/+", "");
        if (normalized.isEmpty()) return "";

        String[] parts = normalized.split("(?U)\\s+", 2);
        String root = parts[0];
        if (root.contains(":")) {
            root = root.split(":", 2)[1];
        }
        return root.replaceAll("[^a-z0-9_\\-]", "");
    }

    private void notifyStaff(Player offender, String command, String serverName) {
        String safePlayer = config.escape(offender.getUsername());
        String safeCommand = config.escape(command);
        String safeServer = config.escape(serverName);

        String msg = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand)
                .replace("{server}", safeServer);

        Component message = config.color(msg);

        if (config.isNotificationActionsEnabled()) {
            List<NotificationAction> actions = config.getNotificationActions();
            for (NotificationAction action : actions) {
                String label = action.getLabel().replace("{player}", safePlayer);
                String hover = action.getHover().replace("{player}", safePlayer);
                String sanitizedPlayer = offender.getUsername().replaceAll("[^a-zA-Z0-9_]", "");
                String cmd = action.getCommand().replace("{player}", sanitizedPlayer);

                Component actionComp = MiniMessage.miniMessage().deserialize(label)
                        .hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(hover)))
                        .clickEvent(ClickEvent.runCommand(cmd));

                message = message.append(actionComp);
            }
        }

        final Component finalMessage = message;
        plugin.getProxy().getAllPlayers().stream()
                .filter(p -> p.hasPermission(config.getNotifyPermission()))
                .forEach(p -> p.sendMessage(finalMessage));
    }
}
