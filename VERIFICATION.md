# Verification: 1.3.0-SNAPSHOT museum, hybrid menus and approved sounds

## Current continuation evidence - 20 October 2026

The Roundtable continuation starts from PR 12 source `49dec87c2e03a1c28b27ae43df8bb66ee20ca5cc`, with canonical main `a650216c6e795f900988b05d788de68b9d4a9866` as an ancestor. It changes the approved mail sound defaults and missing-setting fallbacks, preserves explicit operator settings and success-only playback, and records source and release readiness. This remains an unmerged testing candidate.

- A fresh isolated Java 21/Paper 1.21 `clean build` passes **267 tests**, with zero failures, errors or skips. The suite includes sound/transaction regressions, architecture and compiled call-cycle checks, real SQLite/packaging checks, and all 13 existing museum preview cases.
- EARS validation and all 12 SPEAR helper tests pass. An independent worker source review found no actionable defect in the sound patch. No new API, import, persistence path or playback call site is introduced.
- This local run uses one Gradle worker, an in-process Kotlin compiler and bounded JVM memory. It does not repeat every Paper profile; the existing hosted matrix verifies those profiles on the final pushed commit.
- The prior 49dec87 head had successful Codacy and six Paper/Java checks. Those results are historical for this continuation; inspect the exact final commit in [PR 12](https://github.com/FainNeito/Enthusia-Express/pull/12). A bot status does not imply an independent approval.
- Testing JAR SHA-256: `3bc3651c993515f3be8546bd9c98c5fc4051f04f1b4f1fe1e38f162604533d75`. The JAR is retained outside server plugin directories and has not been installed. `verification-summary.json` records current local results and preserves the older 243-test snapshot as explicitly historical evidence.

The [approved sound profile](docs/roundtable-sound-profile-20261020.md) documents the twelve existing-installation leaf settings. Replacing the JAR does not intentionally overwrite a running server's custom configuration. Earlier recordings exercised the old profile; real listening to this candidate remains pending.

The recovered session retains successful Java artwork/page navigation, Close/sneak/combat restoration and authoritative inventory preservation, as well as earlier recipient, permission and mail behavior evidence. These are carried forward without claiming a retest. [Preview readiness](docs/roundtable-preview-readiness-20261020.md) specifies the remaining 1,200-server-tick per-page expiry, actual Bedrock navigation/rendering/restoration, and legacy artwork with available saved pixels. The Eve/map 444 remains missing-data evidence.

[Release readiness](docs/roundtable-release-readiness-20261020.md) records the distinct canonical and release-fork Tags/network sources. The unique-mail fork must preserve newer Pumpkin Patcher and existing holiday presentation before combined validation. Neither inspected network tree currently has an Express or Holidays gitlink. Current deployed artifact identity, matching Holiday dependencies and shared TEST/client prerequisites remain explicit acceptance gates. No production action or canonical merge occurred in this continuation.

---

## Historical 1.3.0-SNAPSHOT evidence before this continuation

The following record predates the completed museum-preview work and approved-sound continuation. Its counts, artifact, probe and broad pending-client wording describe that earlier snapshot; the current evidence above and linked readiness documents supersede it.

This unmerged testing branch starts at canonical Express main a650216c6e795f900988b05d788de68b9d4a9866. It is not a production release or live client acceptance.

### Automated proof at that snapshot

- Gradle 9.7.1 / Kotlin 2.4.20, Java 21 baseline: clean build and all eleven 1.21 API compilation targets pass.
- 243 tests pass without failures, errors or skips, including architecture, compiled call-cycle inspection, real SQLite migration/concurrency/recovery, postage and bundled-driver packaging.
- The actual Java 21 baseline shaded JAR is tested against final Paper 26.3.build.205-stable on Java 25; 243 tests pass. This is API/runtime-fixture proof, not a running 26.3 Minecraft server.
- Project EARS validation and twelve SPEAR helper regression tests pass. No production Kotlin layer imports Paper or SQL into domain/application.
- A fresh loopback-only Paper 26.2.129 server loads Express and the real public dialog builders, text input and callback options through an isolated probe. Its synthetic Player captures showDialog; no client rendering, inventory, provider or resource-pack acceptance is claimed.

Actual red evidence: missing catalog schema (museum-red.log), missing owner/expiry gate (dialog-session-red.log), unconfigured action-bar delivery (notification-red.log), and missing inventory closure before form transitions (dialog-cargo-red.log). Corresponding regressions and the retained safety suite now pass. Logs and raw XML reports remain local evidence, not repository artifacts.

### Delivery and gates at that snapshot

[Workflow, additive migration, permissions and acceptance](docs/museum-dialogs.md) records the implemented scope and rollback boundaries. [Interactive preview](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site) is an owner-private illustrative simulation. The hosted Site reports a successful deployment; fresh browser/device QA was unavailable in this session. Native client layout, Floodgate/Cumulus responses, actual currency/Nexo configuration and coordinated recovery rehearsals remain staging gates. Prior CombatLogX confirmation is not fresh acceptance of every new path.

Tags PR #26 is green at 111154b2e5c9420a2f9fe962906cb8d46ae973ef but requires an upstream maintainer to merge. Its 254-test verification and Codacy pass do not update the network pin or deployed fork. Reconcile current fork holiday/menu changes and verify the combined build after merge. No production server was changed.

The current artifact hash and precise verification scope are in verification-summary.json. Unmerged artifacts are for isolated testing only; production artifacts must be rebuilt from clean merged source and verified pins.

---

## Historical 1.2.1 evidence (superseded snapshot)

The remainder preserves the earlier safety audit. Its versions, counts and prerelease limitations describe that historical snapshot, not the current branch.

## Enthusia Express 1.2.1 verification

## Release candidate review — 2026-10-01

The current distributable uses Kotlin 2.4.20, Gradle 9.7.1, Java 21 bytecode and SQLite JDBC 3.51.3.0. The review covers the shared Mapart Museum intake, native mail-arrival notifications, inbox/sent controls, individual and bulk text-mail read actions, and the inherited shipment/claim recovery protections.

- The local `clean build verifyPaperCompatibility` passed 210 tests with zero failures or errors and compiled all eleven baseline Paper API targets. Tests include the compiled call-cycle audit and shaded SQLite-driver checks.
- GitHub verifies five representative API/runtime combinations: Paper 1.21, 1.21.8 and 1.21.11 on Java 21; Paper 26.2 and 26.3-pre-2 on Java 25. The Java 25 jobs test the baseline shaded JAR against the newer API.
- The two Codacy findings introduced by bulk-read handling were addressed by separating the eligibility/operation guards and moving completion handling into a documented method. This preserves recipient isolation, duplicate-operation protection and stale-session checks.
- A new real-SQLite/rendering regression reproduced retained sent history being falsely labeled expired because page snapshots omit payloads. Sent cards now use the persisted purge status for expiration and read hints; a separate purged-history regression preserves the actual expired state.
- Full CodeRabbit review identified two additional issues, both reproduced before correction: disabled Currency bindings could not retry after enable, and SQL operator precedence excluded museum rows from only one purge branch. Binding now retries disabled providers without repeating diagnostics; grouping the purge predicate protects both branches. The new tests preserve fail-closed incompatible APIs and payloads in legacy returned museum rows.
- PR #5 was compared with current main and closed as superseded. Its safety changes are retained in main; merging its older tree would remove newer shipping journals, movement leases and complete temporary delivery-receipt recovery.

Publish `v1.2.1-rc.1` as a prerelease for staging. Actual EnthusiaCurrency, CombatLogX, Nexo/resource-pack and Java/Bedrock client acceptance remains outstanding. Native advancement headings are client-controlled; the separately requested editable mail-only display is not included. Final Paper 26.3 remains unverified. The historical safety evidence below records earlier snapshots and is not the current test count or artifact checksum.

## Historical safety verification

Reviewed baseline: merged main `66786088c3988478afe34518d779694b00d882b1`. This safety release contains 34 Kotlin production files and uses Java 21 baseline bytecode, Kotlin 2.3.21, Gradle 9.1.0 and bundled SQLite JDBC 3.51.3.0.

See [Paper 26 compatibility](docs/paper-26-compatibility.md) for exact pinned APIs, reproduction commands and prerelease limits. Final 26.3 is not verified.

## Automated results

- Clean `build verifyPaperCompatibility`: passed; all eleven Paper API compilation targets passed (1.21, 1.21.1 and 1.21.3 through 1.21.11).
- 149 tests passed with the updated toolchain on Paper 1.21 / Java 21 and against the preserved baseline JAR on Paper 26.2 and 26.3-pre-2 / Java 25. No failures, errors or skips. The earlier safety-review toolchain also passed all 149 tests on 1.21.8 and 1.21.11; CI retains those representative jobs. These are API/mocked integration test environments, not running Minecraft servers.
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

The testing artifact was copied from the clean Paper 1.21 baseline before API override runs. Local dependency resolution used an untracked Maven directory containing the unmodified official SQLite POM/JAR; their SHA-256 values were verified against Maven Central. The repository's Gradle configuration continues to resolve the pinned release from Maven Central normally. The Java 25 compatibility runs additionally used an untracked loopback dependency bridge with Python-verified upstream HTTPS and unmodified official artifacts. No local dependency override or bridge is committed.

SPEAR requirements REQ-037 through REQ-043 and tasks TDD-015 and TDD-016 record the safety changes, red/green evidence, import evidence and architecture checks. REQ-044 / INFRA-011 record the Paper 26 build verification.

**Live staging has not been performed.** The actual staging server, installed currency/CombatLogX/Nexo configuration and resource pack were not available. Do not interpret automated success as production approval. Follow [the staging and recovery guide](docs/production-safety.md) before deployment.

Inventory, third-party currency and SQLite do not share an atomic transaction. Abrupt process death or a provider that debits and then throws can still require evidence-based administrator reconciliation. Unknown old pending claims cannot safely be reset automatically. Legacy Bukkit API usage still emits deprecation warnings. Folia is not supported. Chat filtering remains excluded.

Historical testing JAR SHA-256: `20039bc6bfa0aa05c6cd3b3cf882be8c96313386b6d2a1d19c7ee9ed60c687a3`. Use the release asset's checksum for the current artifact.
