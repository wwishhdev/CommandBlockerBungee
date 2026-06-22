# Changelog

All notable changes to CommandBlockerBungee & CommandBlockerVelocity will be documented in this file.

---

## [Unreleased]

### Added

- Added an optional Velocity `proxy-messaging` module for global `/msg`, `/tell`, `/w`, `/m`, and `/r` private messages across all backend servers, disabled by default and configurable with Spanish MiniMessage messages.

### Changed

- Replaced the corrupted BungeeCord startup banner with an ASCII MiniMessage/Adventure banner and removed the remaining `ChatColor` usage from startup output.
- Registered Velocity command and tab-complete listeners with explicit event priority instead of deprecated `Subscribe.order()`.

### Fixed

- Fixed `/cbstatus` reporting `v2.3.0` even though both modules are version `2.4.0`.
- Fixed Velocity 1.13+ command-list filtering by handling `PlayerAvailableCommandsEvent`, so blocked or non-whitelisted root commands are removed from the Brigadier command tree shown when pressing tab after `/`.
- Fixed Velocity tab-complete whitelist mode so allowed commands are also added to the available command tree when the backend/proxy did not already provide them.
- Added Velocity player-name suggestions for whitelisted private-message command roots such as `/msg` and `/tell`.
- Reused command matching normalization for tab-complete filtering in both modules, including blocked command suggestions when whitelist mode is disabled.

### Tests

- Added unit coverage for tab-complete filtering rules in both BungeeCord and Velocity command matchers.

---

## [2.4.0] - 2026-04-16

### Added

- Added a root Maven aggregator so both BungeeCord and Velocity modules can be validated with one `mvn test` or `mvn package` command.
- Added JUnit 5 command-matching tests for both modules, covering exact blocks, plugin-prefix aliases, Unicode invisible character bypasses, wildcard/regex rules, server-specific rules, allowlist priority, invalid regex handling, and deep-scan false-positive protection.

### Changed

- Extracted command-block matching into a platform-local `CommandMatcher` in both modules so the security-critical logic is testable without a live proxy runtime.
- Updated the Velocity startup banner to report version `2.4.0`.
- Normalized case handling with `Locale.ROOT` for command/security comparisons.
- Escaped backend server names before inserting them into MiniMessage staff notifications.
- Disabled Discord webhook mentions through `allowed_mentions` and escaped server names in webhook content.
- Replaced corrupted status separator characters with ASCII separators.

### Fixed

- Fixed `commandblocker.admin` missing from BungeeCord `plugin.yml`, which made `/cbstatus` inconsistent with the documented permission model.
- Fixed server-specific command rules so exact server-name keys work as documented, while still supporting lowercase fallback for existing configs.
- Fixed shutdown persistence ordering by waiting briefly for cooldown saves before clearing memory and closing the database pool.
- Fixed BungeeCord scheduled webhook and cooldown tasks so they are explicitly cancelled during shutdown.
- Fixed audit log line injection risk by escaping CR/LF characters in logged fields.
- Fixed tab-complete whitelist filtering to ignore malformed null entries instead of risking a runtime error.

### Security

- Pinned `com.google.protobuf:protobuf-java` to `3.25.5` in both modules to avoid the vulnerable `3.25.1` version pulled transitively by `mysql-connector-j:8.3.0` (CVE-2024-7254).
- Reduced Discord webhook abuse surface by preventing blocked command content from triggering user, role, or everyone mentions.

### Docs

- Rewrote the README with clean ASCII text, current commands, permission coverage, root build commands, and the exact scope of automated tests.

---

## [2.3.0] - 2026-03-04

### Security Fixes

- **[CRITICAL] Fixed `SimpleDateFormat` thread-safety vulnerability in `FileLogger`**: Replaced non-thread-safe `SimpleDateFormat` with `DateTimeFormatter` (immutable and thread-safe). Under concurrent audit log writes, the old implementation could produce corrupted dates, wrong file names, or runtime exceptions.

- **Expanded Unicode invisible character stripping**: The command sanitizer now strips a much broader range of invisible Unicode characters (`\u00AD`, `\u034F`, `\u061C`, `\u070F`, `\u115F`, `\u1160`, `\u17B4`, `\u17B5`, `\u180E`, `\u200B-\u200F`, `\u202A-\u202E`, `\u2060-\u2064`, `\u2066-\u2069`, `\u206A-\u206F`, `\uFEFF`, `\uFFA0`). Previously only 5 zero-width characters were covered, leaving room for bypass via soft hyphens, bidi overrides, and other invisible chars.

- **`WebhookManager` now resets state on `/cbreload`**: Previously, changing the webhook URL, rate-limit, or disabling the webhook in config required a full proxy restart. Now `WebhookManager.reload()` clears the pending queue and rate-limit map so changes take effect immediately.

- **Velocity scheduled tasks are now properly cancelled on shutdown**: `CooldownManager` and `WebhookManager` in the Velocity module now store their `ScheduledTask` references and cancel them during `onProxyShutdown`. This prevents orphaned tasks and potential errors during hot-reloads.

### Bug Fixes

- **Implemented audit log file rotation (was configured but never enforced)**: The config option `audit-log.max-files` existed and had a getter in `ConfigManager`, but `FileLogger` never used it. Old `.log` files accumulated indefinitely. Now `FileLogger` checks the file count after every write and deletes the oldest files when the limit is exceeded.

### New Features

- **Regex & Wildcard command blocking**: Blocked commands now support three formats:
  - `'op'` - exact match (existing behavior)
  - `'regex:game(mode)?.*'` - Java regex pattern (case-insensitive)
  - `'wildcard:game*'` - glob pattern (`*` = any characters, `?` = single character)
  
  Regex/wildcard entries are skipped during deep-scan (execution-chain argument scanning) to avoid false positives.

- **Auto-Punishments system**: Automatically execute console commands when a player reaches configurable attempt thresholds. Useful for auto-kick after N attempts or auto-ban after repeated abuse.
  ```yaml
  auto-punishments:
    enabled: true
    actions:
      - threshold: 5
        command: 'kick {player} Attempting to use blocked commands'
      - threshold: 10
        command: 'ban {player} Repeated use of blocked commands'
  ```

- **Per-command custom messages**: Override the default block message for specific commands. When a player tries `/op`, they can see "You are not allowed to use the OP command" instead of the generic block message.
  ```yaml
  custom-messages:
    enabled: true
    commands:
      op: '<red>You are not allowed to use the OP command.'
      plugins: '<red>Plugin list is hidden on this server.'
  ```

- **New placeholders for webhooks and notifications**:
  - Webhook `content` now supports: `{player}`, `{command}`, `{server}`, `{uuid}`, `{timestamp}`
  - Staff notification `command-message` now supports: `{player}`, `{command}`, `{server}`
  - Default notification message updated to include server name.

- **Blocked command logging to database**: When the database is enabled, all blocked command events are now logged to a new `cb_command_log` table with columns: `uuid`, `player_name`, `server`, `command`, `timestamp`. This enables historical analysis and auditing beyond the text-based audit log.

- **Tab-complete whitelist mode**: A new mode that hides ALL tab-complete suggestions except explicitly allowed commands. Useful for servers that want to completely hide their plugin stack from players.
  ```yaml
  tab-complete-whitelist:
    enabled: true
    allowed:
      - 'help'
      - 'msg'
      - 'tell'
  ```

- **`/cbstatus` command**: A new diagnostic command (aliases: `/cbinfo`) that displays a real-time dashboard showing:
  - Number of blocked commands (global + server-specific)
  - Status of all features (cooldown, database, webhook, audit log, auto-punishments, custom messages, tab whitelist)
  - Database type, max attempts, online player count
  - Requires `commandblocker.admin` permission.

### Changes

- **Version bumped to 2.3.0** across all modules, configs, and metadata files.
- Default webhook `content` now includes server name: `` `{command}` on server `{server}` ``
- Default notification `command-message` now includes server name in parentheses.
- `WebhookManager` getter added to main plugin class for reload access.
- `DatabaseManager.createTable()` now creates both `cooldowns` and `command_log` tables.

### New Files

- `bungee/utils/PunishmentAction.java` - Data class for auto-punishment thresholds
- `velocity/utils/PunishmentAction.java` - Same for Velocity
- `bungee/commands/StatusCommand.java` - `/cbstatus` command implementation
- `velocity/commands/StatusCommand.java` - Same for Velocity

### New Permissions

| Permission | Description | Default |
|---|---|---|
| `commandblocker.admin` | Access to `/cbstatus` command | OP |

---

## [2.2.0] - Previous Release

- Initial public release with command blocking, cooldowns, database support, Discord webhooks, interactive staff notifications, alias detection, server-specific blocking, and audit logging.
