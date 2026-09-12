# Enthusia Express 1.2.0 verification

Production: 32 Kotlin files, Java 21, Kotlin 2.2.21, Gradle 8.14.3. The shaded JAR includes Kotlin and SQLite 3.50.3.0. Paper, Vault and CombatLogX remain external.

## Executed checks

- Clean baseline `build verifyPaperCompatibility`: passed. The final delivery JAR was rebuilt with a clean Paper 1.21 build after the recipient-block and Nexo changes and copied before later API test runs.
- Paper API 1.21: 132 tests, 0 failures, 0 errors, 0 skips.
- Paper API 1.21.11: 132 tests, 0 failures, 0 errors, 0 skips.
- Paper API 1.21.8: 132 tests, 0 failures, 0 errors, 0 skips.
- All eleven Kotlin API compilation targets passed: 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11.
- Real SQLite tests cover competing writers and initializers, legacy schema migration, claim compensation reservations, broadcasts, recovery, return-to-sender and purge boundaries.
- Vault tests cover bank-only and mixed payment, authoritative withdrawal failure, original-provider/offline refunds, disabled provider handling and operation without Vault classes.
- CombatLogX tests validate typed 11.7 API calls, non-public implementations, missing API classes and fail-closed behavior.
- Reviewed regressions cover post-commit connection-reset failure, durable acknowledgment retry across restart, malformed receipts, reconnect notifications, fractional currency messages, uncached-recipient lookup and book-serialization failure. The expanded focused Detekt 1.23.8 run (INFRA-010 rules, rerun for TDD-014) using `docs/detekt-focused.yml` reports zero findings; this is not a claim that every optional Detekt rule was enabled.
- Three Konsist architecture checks and the compiled project call-graph audit passed. No direct project or lambda-call cycles were detected; arbitrary reflection and external dispatch are outside that static analysis.
- A temporary uncompiled domain fixture with `@jakarta.persistence.Entity` was rejected by the annotation gate, then removed before the clean build.
- Shaded-JAR tests load the bundled SQLite native driver and check runtime contents and exclusions.

## Gameplay feedback verification

Five behavior regressions failed before implementation. The final suite additionally verifies same-window inbox navigation, offline-name suggestions without per-completion file scans, online-letter wording, two-click postage confirmation, invalidated cargo/fee quotes, virtual-currency quote labels, and nonempty singular/plural join notices. Chat filtering is deferred. The Medal clip informed the compact menu titles; actual mouse behavior and client text fit still require server testing.

Sent-history regressions verify sender-only visibility, stable 45-entry pagination, delivery reservation status, original-recipient preservation after return/purge/restart, legacy migration with unknown returned destinations, history permissions, read-only package clicks, sent-book reading without unread mutation, selected category labels and command completion. The clean baseline JAR was copied before the later API override runs.

Block regressions verify persistent UUID preferences, owner-scoped lists, direct and broadcast rejection, cross-connection enforcement, preserved returns, permission/session checks, pre-payment rejection and refund after a concurrent block. Nexo tests cover item cloning, fresh resolution, missing/failed API fallbacks, reflective lookup, configured titles and custom-material placeholder safety. Actual Nexo glyph rendering requires client testing with the installed resource pack.

## SPEAR evidence

Applied BadgersMC SPEAR at 2c91bae046649035f4abaa3c563f6676399e2eee. EARS requirements, failing behavior regressions, implementation, architecture checks and refinement are recorded in docs/requirements.md and docs/tasks.md. Java tests remain independent JVM clients of the Kotlin production code.

## Limits

No live Paper, Nexo, CombatLogX or EnthusiaCurrency server was started. Paper/Vault interactions use mocks; compilation is not live compatibility certification. Legacy Bukkit calls emit deprecation warnings. Test on ordinary Paper using TESTING.md; Folia is not supported.

The migration adds delivery_pending without changing existing payloads/statuses. Abrupt process death can leave uncertain inventory delivery or a pending reservation; inventory files and SQLite are not one atomic store. Back up player data, mail.db and delivery-receipts together. Durable receipts replay acknowledgments after restart; reconcile deliveries without a receipt before clearing uncertain reservations.

JAR SHA-256: `cc67dfda57b0f66b331ce798dde5997270bcb365c4ef22f1bbcab849ebe17826`
