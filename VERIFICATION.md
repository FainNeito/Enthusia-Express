# Enthusia Express 1.2.0 verification

Production: 25 Kotlin files, Java 21, Kotlin 2.2.21, Gradle 8.14.3. The shaded JAR includes Kotlin and SQLite 3.50.3.0. Paper, Vault and CombatLogX remain external.

## Executed checks

- Clean baseline `build verifyPaperCompatibility`: passed. The final delivery JAR was rebuilt with a clean Paper 1.21 build after final documentation-policy and test refinement and copied before later API test runs.
- Paper API 1.21: 100 tests, 0 failures, 0 errors, 0 skips.
- Paper API 1.21.11: 100 tests, 0 failures, 0 errors, 0 skips.
- Paper API 1.21.8: 100 tests, 0 failures, 0 errors, 0 skips.
- All eleven Kotlin API compilation targets passed: 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11.
- Real SQLite tests cover competing writers and initializers, legacy schema migration, claim compensation reservations, broadcasts, recovery, return-to-sender and purge boundaries.
- Vault tests cover bank-only and mixed payment, authoritative withdrawal failure, original-provider/offline refunds, disabled provider handling and operation without Vault classes.
- CombatLogX tests validate typed 11.7 API calls, non-public implementations, missing API classes and fail-closed behavior.
- Reviewed regressions cover post-commit connection-reset failure, durable acknowledgment retry across restart, malformed receipts, reconnect notifications and fractional currency messages. Focused Detekt analysis reports zero findings.
- Three Konsist architecture checks and the compiled project call-graph audit passed. No direct project or lambda-call cycles were detected; arbitrary reflection and external dispatch are outside that static analysis.
- Shaded-JAR tests load the bundled SQLite native driver and check runtime contents and exclusions.

## SPEAR evidence

Applied BadgersMC SPEAR at 2c91bae046649035f4abaa3c563f6676399e2eee. EARS requirements, failing behavior regressions, implementation, architecture checks and refinement are recorded in docs/requirements.md and docs/tasks.md. Java tests remain independent JVM clients of the Kotlin production code.

## Limits

No live Paper, CombatLogX or EnthusiaCurrency server was started. Paper/Vault interactions use mocks; compilation is not live compatibility certification. Legacy Bukkit calls emit deprecation warnings. Test on ordinary Paper using TESTING.md; Folia is not supported.

The migration adds delivery_pending without changing existing payloads/statuses. Abrupt process death can leave uncertain inventory delivery or a pending reservation; inventory files and SQLite are not one atomic store. Back up player data, mail.db and delivery-receipts together. Durable receipts replay acknowledgments after restart; reconcile deliveries without a receipt before clearing uncertain reservations.

JAR SHA-256: `b6164d65b83a63484e72e8c5586425a77e921d481714e08d8a602ce999757f74`
