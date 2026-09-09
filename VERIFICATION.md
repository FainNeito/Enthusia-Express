# Enthusia Express 1.2.0 verification

Production: 23 Kotlin files, Java 21, Kotlin 2.2.21, Gradle 8.14.3. The shaded JAR includes Kotlin and SQLite 3.50.3.0. Paper, Vault and CombatLogX remain external.

## Executed checks

- Clean baseline `build verifyPaperCompatibility`: passed. The delivery JAR was copied from this Paper 1.21 build before later API runs.
- Paper API 1.21: 91 tests, 0 failures, 0 errors, 0 skips.
- Paper API 1.21.11: 91 tests, 0 failures, 0 errors, 0 skips.
- Paper API 1.21.8: 91 tests, 0 failures, 0 errors, 0 skips.
- All eleven Kotlin API compilation targets passed: 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11.
- Real SQLite tests cover competing writers and initializers, legacy schema migration, claim compensation reservations, broadcasts, recovery, return-to-sender and purge boundaries.
- Vault tests cover bank-only and mixed payment, authoritative withdrawal failure, original-provider/offline refunds, disabled provider handling and operation without Vault classes.
- CombatLogX tests validate the 11.7 API signatures and mocked combat, missing-dependency and fail-closed behavior.
- Three Konsist architecture checks and the compiled project call-graph audit passed. No direct project or lambda-call cycles were detected; arbitrary reflection and external dispatch are outside that static analysis.
- Shaded-JAR tests load the bundled SQLite native driver and check runtime contents and exclusions.

## SPEAR evidence

Applied BadgersMC SPEAR at 2c91bae046649035f4abaa3c563f6676399e2eee. EARS requirements, failing behavior regressions, implementation, architecture checks and refinement are recorded in docs/requirements.md and docs/tasks.md. Java tests remain independent JVM clients of the Kotlin production code.

## Limits

No live Paper, CombatLogX or EnthusiaCurrency server was started. Paper/Vault interactions use mocks; compilation is not live compatibility certification. Legacy Bukkit calls emit deprecation warnings. Test on ordinary Paper using TESTING.md; Folia is not supported.

The migration adds delivery_pending without changing existing payloads/statuses. Abrupt process death can leave uncertain inventory delivery or a pending reservation; inventory files and SQLite are not one atomic store. Back up both and reconcile uncertain deliveries before clearing reservations.

JAR SHA-256: `7cd9d51193ab0134a02e73adc46507df42d090f94f9a0782e20e9de346631f5f`
