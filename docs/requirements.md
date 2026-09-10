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

WHERE single-outstanding limits are enabled WHEN a sender submits mail THE SYSTEM SHALL atomically allow at most one outstanding letter and one outstanding package per sender/recipient pair, releasing letters when read and packages when claimed, returned or purged.

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
