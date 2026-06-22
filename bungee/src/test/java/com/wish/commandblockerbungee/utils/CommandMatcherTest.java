package com.wish.commandblockerbungee.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CommandMatcherTest {

    private final TestRules rules = new TestRules();
    private final CommandMatcher matcher = new CommandMatcher(rules);

    @Test
    void blocksExactAliasesAndHelpSubcommand() {
        rules.blocked = List.of("op");

        assertTrue(matcher.isCommandBlocked("/op", "lobby"));
        assertTrue(matcher.isCommandBlocked("//minecraft : op", "lobby"));
        assertTrue(matcher.isCommandBlocked("/op help", "lobby"));
    }

    @Test
    void stripsInvisibleCharactersBeforeMatching() {
        rules.blocked = List.of("op");

        assertTrue(matcher.isCommandBlocked("/o\u200Bp", "lobby"));
        assertTrue(matcher.isCommandBlocked("/o\u202Ep", "lobby"));
    }

    @Test
    void allowedCommandsOverrideBlockedRules() {
        rules.blocked = List.of("help", "op");
        rules.allowed = List.of("help");

        assertFalse(matcher.isCommandBlocked("/help op", "lobby"));
        assertTrue(matcher.isCommandBlocked("/op", "lobby"));
    }

    @Test
    void avoidsFalsePositiveWhenBlockedWordIsNormalArgument() {
        rules.blocked = List.of("op");

        assertFalse(matcher.isCommandBlocked("/tell Steve op", "lobby"));
        assertTrue(matcher.isCommandBlocked("/sudo Steve op", "lobby"));
    }

    @Test
    void supportsWildcardAndRegexRules() {
        rules.blocked = List.of("wildcard:game*", "regex:ban(ip|list)?");

        assertTrue(matcher.isCommandBlocked("/gamemode", "lobby"));
        assertTrue(matcher.isCommandBlocked("/banip Notch", "lobby"));
        assertFalse(matcher.isCommandBlocked("/balance", "lobby"));
    }

    @Test
    void supportsServerSpecificRulesWithExactAndLowercaseFallback() {
        rules.blocked = List.of();
        rules.serverBlocked.put("Lobby", List.of("spawn"));
        rules.serverBlocked.put("hub", List.of("kit"));

        assertTrue(matcher.isCommandBlocked("/spawn", "Lobby"));
        assertTrue(matcher.isCommandBlocked("/kit", "Hub"));
        assertFalse(matcher.isCommandBlocked("/spawn", "survival"));
    }

    @Test
    void invalidRegexDoesNotBlockEverything() {
        rules.blocked = List.of("regex:*invalid");

        assertFalse(matcher.isCommandBlocked("/op", "lobby"));
        assertEquals(1, rules.warnings.size());
    }

    @Test
    void extractsSanitizedBaseCommandForCustomMessages() {
        assertEquals("op", matcher.getBaseCommandForMessage("//minecraft : o\u200Bp help"));
    }

    @Test
    void hidesNonWhitelistedTabSuggestions() {
        assertFalse(matcher.shouldHideTabSuggestion("/msg", "lobby", true, List.of("msg", "ping")));
        assertFalse(matcher.shouldHideTabSuggestion("ping Steve", "lobby", true, List.of("msg", "ping")));
        assertTrue(matcher.shouldHideTabSuggestion("/plugins", "lobby", true, List.of("msg", "ping")));
        assertTrue(matcher.shouldHideTabSuggestion("minecraft:op", "lobby", true, List.of("msg", "ping")));
    }

    @Test
    void hidesBlockedTabSuggestionsWithoutWhitelist() {
        rules.blocked = List.of("op", "plugins", "velocity");

        assertTrue(matcher.shouldHideTabSuggestion("/plugins", "lobby", false, List.of()));
        assertTrue(matcher.shouldHideTabSuggestion("minecraft:op", "lobby", false, List.of()));
        assertFalse(matcher.shouldHideTabSuggestion("/msg", "lobby", false, List.of()));
    }

    private static final class TestRules implements CommandMatcher.Rules {
        private List<String> allowed = new ArrayList<>();
        private List<String> blocked = new ArrayList<>();
        private final Map<String, List<String>> serverBlocked = new HashMap<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public boolean isAllowedCommandsEnabled() {
            return true;
        }

        @Override
        public List<String> getAllowedCommands() {
            return allowed;
        }

        @Override
        public List<String> getBlockedCommands() {
            return blocked;
        }

        @Override
        public List<String> getServerBlockedCommands(String serverName) {
            return serverBlocked.getOrDefault(serverName, List.of());
        }

        @Override
        public boolean isAliasDetectionEnabled() {
            return true;
        }

        @Override
        public boolean isBlockPluginPrefix() {
            return true;
        }

        @Override
        public boolean isBlockHelpSubcommand() {
            return true;
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    }
}
