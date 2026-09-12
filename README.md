# Enthusia Express 1.2.0

A Kotlin plugin requiring Java 21 for Paper 1.21.x. Packages and letters go to offline players; administrators can publish announcements to online or offline players.

## Commands

| Command | Behavior | Permission (in addition to `enthusiaexpress.use`) |
| --- | --- | --- |
| `/mail` or `/mail inbox [packages\|letters\|announcements]` | Open the mailbox. Arrows change pages; tabs change categories. | `enthusiaexpress.inbox` |
| `/mail sent [packages\|letters\|announcements]` | View your sent history, recipients, dates and status. Sent books open without changing the recipient's unread state. | `enthusiaexpress.sent` |
| `/mail send <player>` | Open a shipping inventory for a known offline player. Put one packed shulker box or bundle in the center and confirm. | `enthusiaexpress.packages.send` |
| Click a package | Claim it into an empty inventory slot. | `enthusiaexpress.packages.claim` |
| `/mail letter <player>` | Send a copy of the signed book in your main hand to a known offline player. | `enthusiaexpress.letters.send` |
| `/mail announce <player>` | Send a signed-book announcement to one known player, including online players. | `enthusiaexpress.admin.announce` |
| `/mail announce all` | Send an announcement to a snapshot of all known players, including those currently online. | `enthusiaexpress.admin.announce` |

Write and sign a book with Minecraft's normal book editor, hold it in your main hand, then send it. The original remains yours; text mail has no item fee. Click a letter or announcement to open the book. Reading clears its unread flag, and it can be read again until text retention expires. Broadcast unread state is independent for every recipient. Future first-time players are not included in past broadcasts. `all` is reserved as the broadcast target.

The general use, inbox, package and letter permissions default to everyone. Announcement publishing defaults to operators. `enthusiaexpress.admin` grants the announcement permission. Permissions are checked again inside services; there is no combat bypass permission. Commands require a player because authoring uses a held book. Recipient lookup uses the server's cached player profiles and does not perform a blocking network lookup.

The selected category is marked in green with an arrow and a Selected tooltip. The compact Inbox/Sent indicator shows the page number; a separate button switches between received and sent mail without reopening the window. History cannot be used to claim packages. It includes retained expired metadata, but expired content cannot be reopened. Existing returned packages whose original destination was overwritten before this upgrade show an unknown recipient. New sends preserve the destination through returns. The `enthusiaexpress.sent` permission defaults to everyone.

## Build

Set `JAVA_HOME` to a Java 21 JDK. The included Gradle 8.14.3 wrapper checks the distribution's SHA-256.

```sh
sh ./gradlew clean build
sh ./gradlew verifyPaperCompatibility
```

On Windows, use `gradlew.bat`. Install **`build/libs/EnthusiaExpress-1.2.0.jar`**, the shaded JAR. The `-plain.jar` is not the installable artifact. Kotlin standard library, SQLite and its native libraries are included; Paper, Vault and CombatLogX are not bundled.

The default compile API is Paper 1.21, with Java bytecode level 21. To run the tests with a later API classpath:

```sh
sh ./gradlew clean build -PpaperVersion=1.21.11
```

Always rebuild with no `paperVersion` override for the release artifact. `verifyPaperCompatibility` compiles against Paper 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 and 1.21.11. See `VERIFICATION.md` for executed checks and their limits.

## Installation and configuration

1. Back up `plugins/EnthusiaExpress`, then replace the old plugin JAR with the shaded JAR.
2. Run Paper 1.21.x with Java 21. Install CombatLogX and its own required dependencies when combat protection is required.
3. Restart the server. Review `plugins/EnthusiaExpress/config.yml` and restart after edits.

Existing `mail.db` rows and package byte payloads remain supported. Startup adds a `delivery_pending` column to preserve sending limits during claim delivery; no destructive schema migration is performed. Existing configuration files are preserved. Missing new keys use the defaults below; add them to your existing file if you want to customize them. Invalid numeric ranges or boolean values fail startup instead of silently weakening protection.

| Setting | Default | Purpose |
| --- | --- | --- |
| `mail.require-combatlogx` | `true` | Deny mail if CombatLogX is missing or disabled. |
| `payments.provider` | `auto` | Use EnthusiaCurrency through Vault when installed; otherwise physical inventory raw gold. `physical` forces items; `enthusia-currency` requires the provider. |
| `mail.limits.one-outstanding-package-per-recipient` | `false` | One outstanding package per sender/recipient pair until delivered, returned or purged. |
| `mail.limits.one-outstanding-letter-per-recipient` | `false` | One unread retained letter per sender/recipient pair. Announcements are unlimited. |
| `notifications.join-mail.enabled` | `true` | Notify joining players of claimable packages and unread text mail. |
| `sounds.enabled` | `true` | Enable success cues; customize `package-send`, `package-claim`, `letter-send` and `letter-open` sound, volume and pitch. |
| `mail.raw-gold-per-item` | `1` | Fee per packed item; 0 allows free shipping. |
| `mail.max-recursive-container-depth` | `8` | Reject deeper nesting instead of undercounting it. |
| `mail.return-after-hours` | `168` | Return unclaimed packages to their senders. |
| `mail.purge-returned-after-hours` | `168` | Purge packages still unclaimed after return. |
| `mail.text-retention-hours` | `720` | Retain letters/announcements for this long after sending. |
| `mail.expiration-check-seconds` | `600` | Expiration interval; the first check is one minute after enable. |
| `database.busy-timeout-ms` | `5000` | Wait for competing SQLite writers before failing. |
| `letters.enabled` / `announcements.enabled` | `true` | Enable authoring for the category. Existing mail stays readable. |
| `letters.max-pages` | `50` | Maximum pages for either kind of book mail. |
| `letters.max-payload-bytes` | `262144` | Maximum serialized size for either kind of book mail. |
| `letters.cooldown-seconds` / `announcements.cooldown-seconds` | `10` | Per-player wait since the last successful letter or announcement; reset after restart. |

The configuration file includes editable messages for common results and errors. Some GUI labels and diagnostic messages are fixed in code.

## CombatLogX

The optional typed adapter calls the published `getCombatManager().isInCombat(Player)` API. A present but incompatible or failing CombatLogX installation always blocks access, even when `require-combatlogx` is false. That option permits operation only when CombatLogX is absent/disabled. Hook errors are logged at most once a minute. Access is checked on command entry and again during mailbox callbacks and package confirmation.

## Storage and delivery behavior

Players can use `/mail block <player>`, `/mail unblock <player>` and `/mail blocked [page]` to manage unwanted mail. The `enthusiaexpress.block` permission defaults to true and also requires normal mail access. Preferences persist by UUID. Blocks reject future packages, letters and player-authored announcements, including broadcasts; administrators do not bypass them. Existing mail and return-to-sender recovery remain available. Blocked package sends take no payment; a block committed during a send causes the package and payment to be returned.

Optional Nexo icons and GUI backgrounds are configured under `gui.nexo`; see [Nexo setup](NEXO.md). The integration is disabled by default and falls back to vanilla controls when assets are unavailable.

SQLite runs on one dedicated worker using WAL, `synchronous=FULL` and a configurable busy timeout. Book broadcasts commit as one transaction. Claims use a conditional update, so only one caller wins, including with two repository connections. Expiration is a single transaction: only unclaimed packages return; only returned packages purge; letters and announcements expire separately. Purging erases their payload bytes while retaining the audit row. Claimed package rows remain as audit records.

Item encoding, decoding, inventories and book opening stay on the server thread. GUI state uses inventory identity, preventing stale loads and title-based ownership mistakes. Shipping allows ordinary cursor pickup/placement but blocks shift-click, number-key, double-click and control-slot drag operations. Cancel/close returns the deposited package. Colored bundles are recognized by their bundle metadata. Nested physical container items count toward the shipping fee along with their contents.

Pending claim callbacks recheck connection, combat, permissions, death and inventory space. A failed delivery restores the prior claim state and expiration timestamp. Graceful disable drains pending database completion callbacks before closing SQLite. A failed shipment returns its package and attempts to refund through the original payment provider. A failed currency refund is logged and explicitly reported for administrator recovery; it never creates replacement physical gold. Physical refund overflow drops at the player's location, and an offline physical refund saves player data.

Minecraft player inventory files and SQLite are separate storage systems. Sudden process termination or power loss between an inventory mutation and its database commit can still lose or duplicate items. This is not an exactly-once, crash-atomic delivery system. Graceful shutdown and concurrent database operations are covered by tests; keep backups of both player data and the plugin database together. Item payloads can contain newer Minecraft data, so do not downgrade a server/database after accepting newer items. This plugin targets ordinary Paper, not Folia.

## EnthusiaCurrency and package guidance

Install Vault and EnthusiaCurrency alongside this plugin to use the virtual balance. With `payments.provider: auto`, one authoritative Vault withdrawal spends bank funds first, then the physical currency recognized by EnthusiaCurrency. The plugin does not charge inventory again or rely on a potentially cached balance check. Currency refunds credit the original provider's bank, including when the player disconnected. An installed but unavailable EnthusiaCurrency provider blocks payment; remove it or explicitly choose `physical` to use inventory-only payment.

The shipping slot displays a gray glass pane while empty. Place the packed container there; the marker cannot become cargo or a refunded item. Cursor placement is deferred safely and rechecked against the same open inventory.

A claimed package retains its sending allowance until inventory delivery is acknowledged. Failed delivery restores the original row. After delivery, a receipt in `delivery-receipts/` records the acknowledgment for retry every five seconds and after restart. Back up that directory with `mail.db`. A process crash before the receipt is durable can still leave an uncertain reservation requiring administrator review; do not clear it without checking player inventory and the audit row. Invalid individual sound settings disable only their cue and log a warning.

## Pull request checks

GitHub Actions runs Java 21 builds and tests on Paper API classpaths 1.21, 1.21.8 and 1.21.11, plus compilation against all eleven supported APIs. The successful baseline job publishes a testing JAR; every job publishes available test reports. CodeRabbit and Codacy remain separate review services. Local success does not imply their remote checks have finished.

Player-name suggestions use online players to avoid scanning offline player files during typing. Fully typed names use Paper's cached lookup first, then resolve a missing UUID on a scheduler worker. The result must still belong to a player who has joined this server, and the sender session is rechecked before opening mail.

Existing installations should add `messages.insufficient-currency` from the shipped configuration to customize insufficient combined-balance messages. Raw Gold messages remain specific to physical payments.

## Review documentation policy

Method contracts explain server-thread ownership, asynchronous completion and recovery, including private callbacks. CodeRabbit requires docstring coverage; Codacy's optional `CommentOverPrivateFunction` preference conflicts with that requirement. Files containing these contracts suppress only that documentation-style rule. Other analysis rules and quality gates remain enabled. The isolated SQLite reset-failure regression suppresses `PMD.AvoidAccessibilityAlteration` only for deliberate test fault injection; production connection visibility remains private.
