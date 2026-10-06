# Enthusia Express requirements

Purpose: offline mail for Paper players and server administrators. This Kotlin migration preserves the merged 1.1.0 behavior, SQLite schema, configuration and permissions.

## REQ-001 — Kotlin implementation

THE SYSTEM SHALL implement every production class in Kotlin and package the Kotlin runtime inside the installable JAR.

## REQ-002 — Packages

WHEN a permitted player sends or claims a package THE SYSTEM SHALL retain the existing inventory compensation, recipient checks, conditional claims and gold fees.

## REQ-003 — Letters and announcements

WHEN a permitted player sends a signed book THE SYSTEM SHALL persist a copy with independent recipient unread state and enforce administrator permission for announcements.

## REQ-004 — Combat protection

WHILE CombatLogX reports a player in combat THE SYSTEM SHALL deny mail access according to the existing permission and dependency configuration.

## REQ-005 — SQLite behavior

THE SYSTEM SHALL preserve the existing SQLite schema, serialized writer access, transactional broadcasts and return-to-sender expiration transitions.

## REQ-006 — Iterative traversal

THE SYSTEM SHALL traverse nested shipping containers without direct or indirect recursive project method calls and preserve nesting limits and checked arithmetic.

## REQ-007 — Hexagonal boundaries

THE SYSTEM SHALL keep domain types independent of application and infrastructure, and restrict application code to domain types and standard libraries.

## REQ-008 — Test artifact

WHEN the Kotlin migration is complete THE SYSTEM SHALL produce a shaded Java 21 test JAR targeting Paper 1.21 with SQLite native resources and an accurate verification report.

## REQ-009 — Regression coverage

THE SYSTEM SHALL pass the existing 38 regression tests, Kotlin packaging checks and SPEAR architecture checks before the test JAR is delivered.

## REQ-010 — Configuration compatibility

THE SYSTEM SHALL retain existing command names, permission nodes, messages, configuration keys and stored item payloads without a destructive database migration.

## REQ-011 — API checks

THE SYSTEM SHALL compile the Kotlin production source against all 11 previously verified published Paper 1.21 API configurations and run tests on API 1.21, 1.21.8 and 1.21.11.

## REQ-012 — Package slot guidance

WHEN a player opens shipping THE SYSTEM SHALL display a gray glass package-slot marker that cannot be extracted, shipped or refunded and can be replaced safely with a package.

## REQ-013 — Outstanding sending limits

WHERE single-outstanding limits are enabled WHEN a sender submits mail THE SYSTEM SHALL atomically allow at most one outstanding letter and one outstanding package per sender/recipient pair, releasing letters when read and packages only after delivery acknowledgment, return or purge. A claim with delivery_pending = 1 shall retain its reservation; failed-delivery compensation shall restore the original outstanding package.

## REQ-014 — Join notification

WHEN a player joins with unread active mail THE SYSTEM SHALL send one configurable notification after an asynchronous database query, without notifying disconnected sessions or empty mailboxes.

## REQ-015 — Mail sounds

WHEN mail is successfully sent or accepted THE SYSTEM SHALL play the configured event sound with configurable enabled state, volume and pitch, without playing success sounds for failed operations.

## REQ-016 — EnthusiaCurrency postage

WHEN a player sends a package THE SYSTEM SHALL charge postage once through EnthusiaCurrency's combined virtual and physical balance when that provider is available, preserve physical raw gold payments when absent or explicitly configured, and refund through the original payment provider when persistence rejects or fails.

## REQ-017 — Payment failures

IF the configured currency provider is unavailable or rejects a withdrawal THEN THE SYSTEM SHALL retain the package without submitting mail or attempting an additional physical charge, and report a failed refund without claiming that the fee was restored.

## REQ-018 — Claim compensation preserves sending limits

WHILE a claimed package awaits server-thread delivery THE SYSTEM SHALL reserve its outstanding-package allowance until delivery succeeds or compensation restores the original row, including across independent SQLite connections.

## REQ-019 — Repeatable verification

WHEN a pull request or main branch update is submitted THE SYSTEM SHALL run Java 21 Gradle verification on supported representative Paper API classpaths and publish the baseline testing artifact and test reports, with documentation distinguishing automated checks from live server testing.

## REQ-020 — Nonblocking command suggestions

WHEN a player requests mail tab completion THE SYSTEM SHALL suggest matching online player names without enumerating offline player files, while retaining explicit cached offline-recipient command lookup.

## REQ-021 — Reviewable infrastructure

THE SYSTEM SHALL preserve the tested mail transitions while separating validation, preparation and completion responsibilities and documenting deliberate exception boundaries used for transaction recovery, plugin integration and shutdown.

## REQ-022

When a transaction commits successfully and restoring auto-commit fails, the plugin shall preserve the successful mail result while recovering or retiring the damaged connection.

## REQ-023

When an acknowledgment fails after package delivery, the plugin shall retain a durable retry receipt and retry acknowledgment without restoring or redelivering the package.

## REQ-024

When an enabled CombatLogX implementation implements its published API through a non-public class, the plugin shall apply the published combat status safely.

## REQ-025

When EnthusiaCurrency rejects postage for insufficient funds, the plugin shall display the currency balance without truncating fractional units or describing it as physical Raw Gold.

## REQ-026

WHEN book serialization fails THE SYSTEM SHALL log the player identity and cause, send the configured failure message and skip persistence.

## REQ-027

WHEN a known recipient is absent from the runtime cache THE SYSTEM SHALL resolve its UUID without blocking the server thread and revalidate the sender session before sending.

## REQ-028

WHEN inbox navigation is clicked THE SYSTEM SHALL reuse the active inventory without resetting the mouse cursor and display compact menu titles.

## REQ-029

WHEN a letter targets a player visibly online to the sender THE SYSTEM SHALL explain that a letter is unnecessary because the recipient is online.

## REQ-030

WHEN a player completes a recipient name THE SYSTEM SHALL suggest known offline names from a cached directory without enumerating player files during completion.

## REQ-031

WHEN a sender requests shipment THE SYSTEM SHALL display the postage before charging and require confirmation of the unchanged package and price.

## REQ-032

WHEN a player joins with mail THE SYSTEM SHALL notify only nonempty categories with correct singular or plural wording and the matching inbox command.

## REQ-033

WHEN a player opens sent mail THE SYSTEM SHALL show only that sender's paginated history with original recipients, dates and statuses without permitting claims or changing unread state.

## REQ-034

WHEN a mailbox category is selected THE SYSTEM SHALL visibly highlight its category control and show a compact page and Inbox or Sent indicator without reopening the window.

## REQ-035

WHEN a recipient blocks a sender THE SYSTEM SHALL persist the block and reject future direct mail and broadcast deliveries from that sender while retaining existing mail and return-to-sender recovery.

## REQ-036

WHEN configured Nexo assets are available THE SYSTEM SHALL use their custom GUI icons and title glyphs while falling back to vanilla controls when the optional integration is unavailable.

## REQ-037

WHEN a shipping payment invokes external callbacks THE SYSTEM SHALL reserve cargo and prevent reentrant sends before invoking the provider and compensate an invalidated session at most once.

## REQ-038

IF a known-undelivered claim cannot be restored THEN THE SYSTEM SHALL durably retry its exact claim generation after restart without restoring delivered or later claims.

## REQ-039

WHEN packages are submitted or mailbox pages are displayed THE SYSTEM SHALL enforce configured payload and traversal budgets and render lightweight package summaries.

## REQ-040

WHILE a mailbox lookup is pending THE SYSTEM SHALL coalesce navigation to the latest requested view and bound database admission and per-tick completion work without silently discarding accepted writes.

## REQ-041

THE SYSTEM SHALL bundle a SQLite JDBC release carrying the WAL-reset corruption fix and pass migration and concurrent-connection regressions.

## REQ-042

WHEN complete temporary claim receipts survive a restart THE SYSTEM SHALL validate and replay them without accepting malformed recovery metadata.

## REQ-043

WHEN a currency withdrawal is about to be invoked THE SYSTEM SHALL persist a reconciliation intent and retain it on ambiguous provider failure without automatically refunding an unknown debit.

## REQ-044

THE SYSTEM SHALL verify its build and regression suite against pinned Paper 26.2 and available 26.3 prerelease APIs on Java 25 while retaining the Java 21 and Paper 1.21 baseline distributable.

## REQ-045

WHEN a failed shipment waits for a currency movement lease THE SYSTEM SHALL retain durable cargo, account and original postage route across restart, retry only definitely unattempted compensation under an owned lease, and hold uncertain asset mutations for operator reconciliation without automatic replay.

## REQ-046 — Bulk read

WHEN a recipient selects Mark All Text Read in a received Letters or Announcements inbox THE SYSTEM SHALL atomically clear unread state for that recipient's retained unclaimed letters and announcements across every page. Packages, other recipients and sent-history controls shall remain unaffected; duplicate clicks and stale-session completions shall not create duplicate writes or reopen obsolete menus.

## REQ-047 — Shared museum intake

WHEN an authorized sender submits filled maps THE SYSTEM SHALL validate bounded map-only cargo and charge configured postage before publishing one token-identified museum submission. Managers shall claim only one eligible generation and mark a submission processed only after confirmed delivery. Museum intake shall remain separate from personal mail and expiration.

## REQ-048 — Cosmetic mail arrivals

WHEN a connected session has newly pending mail THE SYSTEM SHALL display a grouped read-only arrival notification with sender and type for one arrival or total mail count for multiple arrivals. Missing native toast bindings shall affect only the configured cosmetic fallback and shall not mutate mail or register server advancements.

## REQ-049 — Recipient presence privacy

WHEN a recipient is hidden from the sender by Paper visibility THE SYSTEM SHALL accept ordinary mail using offline-recipient semantics, without revealing the hidden session through online-only rejection. Visible online recipients, self-mail, blocking, postage and compensation policies shall remain unchanged. Shipping shall recheck visible presence before payment and after provider callbacks. Shared museum intake shall remain exempt from personal-recipient online checks.

## REQ-050 — Event delivery API

WHEN an integrating plugin (EnthusiaHolidays) requests an event delivery through the public `EventDelivery` service THE SYSTEM SHALL wrap the supplied items (1–27 non-empty stacks within the configured nesting limit) in one shulker-box package from the given display sender and store it for the recipient without postage, outstanding-mail limits or sender blocks. Event packages SHALL never be returned, expired or purged by retention, and SHALL be claimed through the normal mailbox with the existing claim/acknowledgment safety. A caller-supplied delivery token SHALL make retries idempotent: the same token always resolves to the first package without storing another, and a token reused for a different recipient SHALL be rejected. The service SHALL use only JDK and Bukkit types and SHALL be registered in Bukkit's ServicesManager.
