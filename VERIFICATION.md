# Verification: 1.3.0-SNAPSHOT museum, hybrid menus and approved sounds

## Current continuation evidence — 20 October 2026

The [Java acceptance ledger](docs/roundtable-java-acceptance-20261020.md) reconciles source, hosted checks, installed-build client evidence and remaining work. The user has deferred Bedrock while the remaining Java tasks are completed. This remains an unmerged testing candidate.

- The final native Java 21/Paper 1.21 clean build completed at **21:55:22.406465 UTC**: **285 tests**, zero failures/errors/skips. EARS and all **12 SPEAR helper tests** passed. The independently reviewed seven-file event-delivery recovery patch is published as `e7bc6109e66a89c08f40c9e3aa6bbe48bb1322f1`. The [native recovery evidence](docs/evidence/event-delivery-recovery-20261020.json) records the source tree, 145 verified baseline inputs, final seven file hashes/Git blobs, suites and artifacts.
- Tests-first RED against the exact unchanged `ddb27dc69a9efd5eaa1fa3c5c6e9a256411bd08f` production source executed **20 tests with 10 behavioral failures, zero errors/skips**. Focused GREEN executed **30 tests with zero failures/errors/skips**. These are native execution results, separate from the installed-build client evidence below.
- At the coordinator's **22:05:42 UTC** verification, new-commit [hosted run 39505007021](https://github.com/FainNeito/Enthusia-Express/actions/runs/39505007021) had completed **all six jobs successfully**, including all eleven API compilation targets. The baseline clean-build step passed (the exact 285-test XML count above is from the native run). Codacy check `118912063119` passed with zero annotations at `e7bc610`. Historical run 39476819886 and the earlier **267-test** result belong to `ddb27dc` and do not verify this recovery candidate.
- New candidate JAR: **16,783,743 bytes**, SHA-256 `d0a20f4eb2c22ff13dd09ce2536f8f4ef76a62ff344eee4d1d80cd3b13b8bf5d`. It is retained and **not installed**; the sound config remains unapplied. The installed preview build and exact rollback copy have SHA-256 `c6afbcf7a4d5572834f9b8a639b833dfa3aef46fb610dfc14e7b115fa78d5197`.
- **Actual Java uninterrupted timeout passed:** map still visible at 59.5 seconds, original compass restored by 60.5 seconds at approximately 20 TPS; authoritative inventory/database/WAL/config unchanged.
- **Actual Java per-page lifetime passed on the installed preview build:** the two commands were 48.901931 seconds apart. Page 1 remained visible beyond page 0's old nominal deadline; the later checkpoint log records the map at 21:23:19.245158 UTC and the compass restored at 21:23:19.745098 UTC. These are capture times, 60.000657 and 60.500597 seconds after the second command, not exact server display or tick timestamps. The silent 110-second video covers the switch and old deadline; separate checkpoints cover expiry. Inventory, selected slot, database/WAL, config and submission state were unchanged. SHARED 612 released claim 600 at **21:25 UTC**.
- **Actual legacy rendering passed:** existing submission 66 rendered saved map 0 with 64 copies, then closed without altering inventory, claim or review state. Submission 70/map 444 remains a separate missing-data case.
- **Approved sound audition passed by user confirmation:** the four cues were delivered once and the user said “The sounds work.” The exact TEST config migration is prepared/reviewed but unapplied; actual Express send/open/claim dispatch on that profile remains pending.
- **Tags composition is complete:** release-fork `a38a0c1ca961adbc86fae3de7601b7ee262bb852` preserves unique-mail and Pumpkin Patcher changes, passed combined verification and shaded SQLite proof, and retains renderer `01fac426152737c76cd4d2e570e07a33dde99798`. It is unmerged and uninstalled.

The [sound profile](docs/roundtable-sound-profile-20261020.md), [preview readiness](docs/roundtable-preview-readiness-20261020.md) and [release readiness](docs/roundtable-release-readiness-20261020.md) distinguish dated worker observations from the current ledger. Java per-page lifetime acceptance is complete for the installed preview build; it does not activate or validate the new source candidate. SHARED 641 records Market's reviewed host-request draft as complete and unsubmitted; SHARED-02 recovery evidence remains technically unverified. Market 654 separately reports the owner's instruction to disregard the inventory issue and perform one graceful TEST restart in the 21:59–22:11 UTC window, with Market as sole executor and **no Express JAR/config swap**. That bounded authorization is not recovery clearance or acceptance of Express mail workflows. Express activation needs fresh acknowledged scope/claim, and actual recipient work retains its specific gate disposition.

**Post-restart update, SHARED 664:** Market reports the single owner-authorized restart completed and the lifecycle/console claim released at 22:05:18 UTC. Server startup completed at 22:02:33 UTC. The fresh plugin receipt identifies the existing Express c6afbcf7 build and the expected Staff candidate with storage ACTIVE; it does not establish complete per-player restoration snapshots. Market 668 separately claims FainNeito for its own testing through 22:25 UTC. Express holds no live resource and has queued no activation or client action.

Holidays PR 35 at `ba2fcf1471e5674e377b0df697f5dd87b63d20a6` is source-compatible according to independent review, but remains draft and uninstalled. Holiday 657 reports canonical CI passing 327 tests in each of three Paper jobs and all eleven API compilation targets; the run/jobs and source tree have an independent review, while independent XML-count verification remains pending. Canonical integration, joint actual-provider acceptance and later merged-source/network pin decisions remain separate. Native compute claim 644 was explicitly released in SHARED 659 at 22:01:38 UTC; the coordinator reports no remaining Gradle/Kotlin process and SPEAR idle.

### Conditional event-delivery defect and locally verified reconciliation

[TDD-073](docs/tasks.md) / [REQ-067](docs/requirements.md) records a source-proven conditional exposure in `ddb27dc`: validation and packaging run before a retry can resolve an existing token. If a package committed, its reply was lost and a later retry encounters stricter container policy, `IllegalArgumentException` can make the Holidays consumer treat an already stored delivery as a definite pre-storage rejection. The event insert also omits the configured package-payload byte limit. No runtime incident is asserted. Completion of the earlier TDD-047 / REQ-050 scope does not close this newly identified case.

The independently reviewed seven-file source/test recovery patch introduces serialized token reconciliation, private in-flight ownership and the new-token payload bound while retaining the public API and schema. Its actual RED, GREEN and 285-test clean-build results and published identity are recorded above. New-commit hosted verification has passed; native and hosted verification do not establish runtime adoption or joint provider acceptance. The historical 267-test result and hosted run remain attributed to `ddb27dc`.

No production deployment, canonical merge or network pin change occurred. Historical results below are preserved with their original scope.

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

