# Changelog

All notable changes to CommandBlockerBungee & CommandBlockerVelocity will be documented in this file.

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
