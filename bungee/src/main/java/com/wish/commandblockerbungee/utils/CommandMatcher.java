package com.wish.commandblockerbungee.utils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CommandMatcher {

    public interface Rules {
        boolean isAllowedCommandsEnabled();

        List<String> getAllowedCommands();

        List<String> getBlockedCommands();

        List<String> getServerBlockedCommands(String serverName);

        boolean isAliasDetectionEnabled();

        boolean isBlockPluginPrefix();

        boolean isBlockHelpSubcommand();

        void warn(String message);
    }

    private static final Set<String> EXECUTION_CHAIN_COMMANDS = Set.of(
            "execute", "sudo", "shell", "run", "cmd", "console"
    );
    private static final Pattern LEADING_SLASHES = Pattern.compile("^/+");
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
            "[\\u00AD\\u034F\\u061C\\u070F\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\u206A-\\u206F\\uFEFF\\uFFA0]"
    );
    private static final Pattern COLON_SPACING = Pattern.compile("\\s*:\\s*");
    private static final Pattern UNICODE_SPACES = Pattern.compile("(?U)\\s+");

    private final Rules rules;

    public CommandMatcher(Rules rules) {
        this.rules = rules;
    }

    public boolean isCommandBlocked(String command, String serverName) {
        String cleanCommand = normalize(command);
        if (cleanCommand.isEmpty()) return false;

        String[] parts = UNICODE_SPACES.split(cleanCommand, 2);
        if (parts.length == 0) return false;

        String baseCommand = parts[0];
        if (baseCommand.isEmpty()) return false;

        if (isAllowed(baseCommand, cleanCommand)) return false;

        if (matchesBlockedList(baseCommand, cleanCommand, parts, safeList(rules.getBlockedCommands()))) {
            return true;
        }

        List<String> serverRules = safeList(rules.getServerBlockedCommands(serverName));
        if (matchesBlockedList(baseCommand, cleanCommand, parts, serverRules)) {
            return true;
        }

        if (serverName != null) {
            String lowerServerName = serverName.toLowerCase(Locale.ROOT);
            if (!lowerServerName.equals(serverName)) {
                return matchesBlockedList(
                        baseCommand,
                        cleanCommand,
                        parts,
                        safeList(rules.getServerBlockedCommands(lowerServerName))
                );
            }
        }

        return false;
    }

    public String getBaseCommandForMessage(String command) {
        String cleanCommand = normalize(command);
        if (cleanCommand.isEmpty()) return "";

        String baseCommand = UNICODE_SPACES.split(cleanCommand, 2)[0];
        if (baseCommand.contains(":")) {
            baseCommand = baseCommand.split(":", 2)[1];
        }
        return baseCommand;
    }

    public boolean shouldHideTabSuggestion(String suggestion, String serverName, boolean whitelistEnabled, List<String> whitelistAllowed) {
        String cleanSuggestion = normalize(suggestion);
        if (cleanSuggestion.isEmpty()) return false;

        if (whitelistEnabled) {
            return !isWhitelistedTabSuggestion(cleanSuggestion, whitelistAllowed);
        }

        return isCommandBlocked(cleanSuggestion, serverName);
    }

    private String normalize(String command) {
        if (command == null || command.trim().isEmpty()) return "";

        String cleanCommand = command.trim().toLowerCase(Locale.ROOT);
        cleanCommand = LEADING_SLASHES.matcher(cleanCommand).replaceAll("");
        cleanCommand = INVISIBLE_CHARS.matcher(cleanCommand).replaceAll("");
        cleanCommand = COLON_SPACING.matcher(cleanCommand.trim()).replaceAll(":");
        return cleanCommand.trim();
    }

    private boolean isWhitelistedTabSuggestion(String cleanSuggestion, List<String> whitelistAllowed) {
        for (String allowed : safeList(whitelistAllowed)) {
            String cleanAllowed = normalize(allowed);
            if (cleanAllowed.isEmpty()) continue;
            if (cleanSuggestion.equals(cleanAllowed) || cleanSuggestion.startsWith(cleanAllowed + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowed(String baseCommand, String cleanCommand) {
        if (!rules.isAllowedCommandsEnabled()) return false;

        for (String allowed : safeList(rules.getAllowedCommands())) {
            if (allowed == null) continue;
            String allowedLower = allowed.toLowerCase(Locale.ROOT);
            if (baseCommand.equals(allowedLower) || cleanCommand.startsWith(allowedLower + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBlockedList(String baseCommand, String cleanCommand, String[] parts, List<String> blockedCommands) {
        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;

            String blockedLower = blockedCmd.toLowerCase(Locale.ROOT);
            if (blockedLower.startsWith("regex:")) {
                String pattern = blockedCmd.substring(6);
                try {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(baseCommand).matches()) {
                        return true;
                    }
                } catch (PatternSyntaxException e) {
                    rules.warn("Invalid regex pattern in blocked-commands: '" + pattern + "' - " + e.getMessage());
                }
                continue;
            }

            if (blockedLower.startsWith("wildcard:")) {
                String pattern = blockedCmd.substring(9).toLowerCase(Locale.ROOT);
                if (wildcardToPattern(pattern).matcher(baseCommand).matches()) return true;
                continue;
            }

            if (baseCommand.equals(blockedLower)) return true;

            if (rules.isAliasDetectionEnabled()) {
                if (rules.isBlockPluginPrefix() && baseCommand.contains(":")) {
                    String[] cmdParts = baseCommand.split(":", 2);
                    if (cmdParts.length > 1 && cmdParts[1].equals(blockedLower)) return true;
                }

                if (rules.isBlockHelpSubcommand()) {
                    if (cleanCommand.equals(blockedLower + " help") || cleanCommand.startsWith(blockedLower + " help ")) {
                        return true;
                    }
                }
            }
        }

        if (parts.length > 1 && EXECUTION_CHAIN_COMMANDS.contains(baseCommand)) {
            String[] allTokens = UNICODE_SPACES.split(parts[1]);
            for (String token : allTokens) {
                String cleanToken = token.contains(":") ? token.split(":", 2)[1] : token;
                for (String blockedCmd : blockedCommands) {
                    if (blockedCmd == null) continue;
                    String blockedLower = blockedCmd.toLowerCase(Locale.ROOT);
                    if (blockedLower.startsWith("regex:") || blockedLower.startsWith("wildcard:")) continue;
                    if (cleanToken.equalsIgnoreCase(blockedCmd)) return true;
                }
            }
        }

        return false;
    }

    private Pattern wildcardToPattern(String wildcard) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '^':
                case '$':
                case '|':
                case '+':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : Collections.emptyList();
    }
}
