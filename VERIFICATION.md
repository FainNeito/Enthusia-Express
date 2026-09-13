# Enthusia Express 1.2.1 verification

Reviewed baseline: merged main `66786088c3988478afe34518d779694b00d882b1`. This safety release contains 34 Kotlin production files and uses Java 21, Kotlin 2.2.21, Gradle 8.14.3 and bundled SQLite JDBC 3.51.3.0.

## Automated results

- Clean `build verifyPaperCompatibility`: passed; all eleven Paper API compilation targets passed (1.21, 1.21.1 and 1.21.3 through 1.21.11).
- 149 tests passed on each representative Paper API classpath: 1.21, 1.21.8 and 1.21.11. No failures, errors or skips. These are API/mocked integration test environments, not running Minecraft servers.
- Focused Detekt reports zero findings using `docs/detekt-focused.yml`.
- Konsist architecture and compiled project/lambda call-graph checks passed. No project call cycles were detected; arbitrary reflection and external dispatch are outside this check.
- Shaded-JAR tests loaded the bundled native driver and asserted SQLite runtime version 3.51.3. Paper, Vault and CombatLogX are not bundled.
- Three new regression tests reproduced unsafe callback shipping, oversized submissions and pending navigation before implementation (`safety-red.log`). The original production review independently reproduced the failed-restoration path with real SQLite.

## Review refinements

Three additional regressions failed before the review fixes (`safety-review-red.log`). Complete temporary restoration receipts now recover after restart; incomplete receipts remain held. Currency withdrawal intents are forced before invoking the provider; unavailable storage prevents a debit, and ambiguous exceptions retain operator evidence without an automatic refund. Ordered payment verification, explicit scanner-budget assertions, callback-count test isolation and JDBC cursor checks were strengthened. The final 149-test results are in the `reviewed-*-results` evidence directories; `safety-reviewed-clean-matrix.log` records the revised clean build.

## Safety regressions

The new tests exercise currency callbacks that reenter confirmation and close shipping menus, rejected withdrawals, failed provider resolution, and original-provider compensation. Cargo is reserved before external calls and never both returned and submitted.

Real SQLite tests exercise a write lock exceeding the busy timeout, durable restoration across repository/journal restart, preserved expiration timestamps, returned-package recovery, receipt-directory write failures, stale receipt replay after another claim, and stale snapshot rejection. Unknown claims remain held and acknowledged delivery cannot be restored.

Resource tests verify oversized shipment rejection before payment, payload-free history/inbox pages without deleting existing contents, one query per player while navigation is pending, cancellation of queued navigation after close, bounded callback batches, and queue saturation followed by graceful draining of every accepted write.

Existing currency, CombatLogX, Nexo, GUI, block, notification, sound, migration, SQLite concurrency and return-to-sender regressions remain in the suite.

## Evidence and limits

The testing artifact was copied from the clean Paper 1.21 baseline before API override runs. Local dependency resolution used an untracked Maven directory containing the unmodified official SQLite POM/JAR; their SHA-256 values were verified against Maven Central. The repository's Gradle configuration continues to resolve the pinned release from Maven Central normally. No local dependency override is committed.

SPEAR requirements REQ-037 through REQ-043 and tasks TDD-015 and TDD-016 record the changes, red/green evidence, import evidence and architecture checks.

**Live staging has not been performed.** The actual staging server, installed currency/CombatLogX/Nexo configuration and resource pack were not available. Do not interpret automated success as production approval. Follow [the staging and recovery guide](docs/production-safety.md) before deployment.

Inventory, third-party currency and SQLite do not share an atomic transaction. Abrupt process death or a provider that debits and then throws can still require evidence-based administrator reconciliation. Unknown old pending claims cannot safely be reset automatically. Legacy Bukkit API usage still emits deprecation warnings. Folia is not supported. Chat filtering remains excluded.

Testing JAR SHA-256: `20039bc6bfa0aa05c6cd3b3cf882be8c96313386b6d2a1d19c7ee9ed60c687a3`
