# Java acceptance and release evidence — 20 October 2026

## Scope

This ledger reconciles the completed Roundtable work with the user's current instruction: finish remaining EnthusiaExpress work except Bedrock. It distinguishes the tested source candidate, the installed preview build, cosmetic sound audition and actual mail workflows. It does not authorize production deployment or a canonical merge.

## Exact source and artifact identities

| Item | Identity | Disposition |
| --- | --- | --- |
| Express tested recovery candidate | `e7bc6109e66a89c08f40c9e3aa6bbe48bb1322f1` | Published; seven source/test files under TDD-073 / REQ-067 independently reviewed; RED/GREEN and final 285-test native build completed. All six hosted jobs and eleven API compilation targets passed. |
| Current candidate JAR | 16,783,743 bytes; SHA-256 `d0a20f4eb2c22ff13dd09ce2536f8f4ef76a62ff344eee4d1d80cd3b13b8bf5d` | Retained and not installed. |
| Historical sound candidate | `ddb27dc69a9efd5eaa1fa3c5c6e9a256411bd08f`; 16,777,084-byte JAR SHA-256 `3bc3651c993515f3be8546bd9c98c5fc4051f04f1b4f1fe1e38f162604533d75` | Earlier 267-test and hosted results belong to this candidate. Its unchanged production source was used for the recovery RED execution. |
| Installed preview JAR / exact rollback | 16,776,801 bytes; SHA-256 `c6afbcf7a4d5572834f9b8a639b833dfa3aef46fb610dfc14e7b115fa78d5197` | Actual Java preview acceptance below applies to this build, associated with 3a4b45e/64e5cdc. Exact rollback copy retained. |
| Prepared TEST sound config | 5,516 bytes; SHA-256 `c87f0c3fa10977d938c63509bc911296ae831cae8b7438a0761c2d00b2a9ca6b` | Independently reviewed; all 12 fields verified, seven changed; not applied. |
| Existing TEST sound config | 5,516 bytes; SHA-256 `ec02f2c22ffa712fea3aeb134c9ac5463720fae63de084cfe97fd3e7c0ded298` | Unchanged through the later per-page acceptance postcheck; claim released at 21:25 UTC. |
| Combined Tags source | `a38a0c1ca961adbc86fae3de7601b7ee262bb852` | Unique-mail and Pumpkin Patcher composition reviewed; unmerged and uninstalled. |
| Combined Tags candidate JAR | SHA-256 `e1e9cacac9d8a587bffdcc5a6b030220e164c337958ee3a0ec575e0e3c104c01` | Retained; renderer companion remains `01fac426152737c76cd4d2e570e07a33dde99798`. |
| Holidays PR 35 | `ba2fcf1471e5674e377b0df697f5dd87b63d20a6` | Source-compatible according to independent review; draft and uninstalled. Canonical integration and joint actual-provider acceptance remain pending. |

TestBridge's runtime report fingerprints the code-source file and may cache by path/size/mtime. It is not JVM bytecode attestation. The installed Express filename, size, timestamp, empty update directory and exact local rollback hash support the retained deployment identity; no new sound-candidate activation is inferred.

## Completed verification

- **Recovery RED:** tests-first execution against exact unchanged `ddb27dc69a9efd5eaa1fa3c5c6e9a256411bd08f` production source ran 20 tests with 10 behavioral failures, zero errors and zero skips. These are observed regression failures, not compilation or fixture errors.
- **Recovery GREEN:** the focused suite passed 30 tests with zero failures/errors/skips after the independently reviewed seven-file recovery change.
- **Final native build:** the Java 21 / Paper 1.21 clean build completed at **21:55:22.406465 UTC**, passing **285 tests with zero failures/errors/skips**. EARS and all **12 SPEAR helper tests** passed. Published source commit: `e7bc6109e66a89c08f40c9e3aa6bbe48bb1322f1`. The [native recovery evidence](evidence/event-delivery-recovery-20261020.json) records 145 verified baseline inputs, the final seven file hashes/Git blobs, source tree, suites and artifacts; no canonical source-archive hash is invented. Native claim 644 was released in SHARED 659 at 22:01:38 UTC, with no Gradle/Kotlin process remaining and SPEAR idle.
- **Current hosted checks:** at the coordinator's **22:05:42 UTC** verification, [run 39505007021](https://github.com/FainNeito/Enthusia-Express/actions/runs/39505007021) had completed **all six jobs successfully**, including all eleven API compilation targets; the baseline clean-build step passed (the exact 285-test XML count above is from the native run). Codacy check `118912063119` passed with zero annotations at `e7bc610`.
- **Historical sound-candidate proof:** the earlier `ddb27dc` local build passed 267 tests. [Hosted run 39476819886](https://github.com/FainNeito/Enthusia-Express/actions/runs/39476819886) passed at that exact code commit, with six Paper/Java jobs, eleven API compilation targets and a zero-finding Codacy result. These are historical results for `ddb27dc`, not hosted verification of the recovery candidate.
- **Tags composition:** [run 39482446400](https://github.com/FainNeito/EnthusiaTags/actions/runs/39482446400) passed at `a38a0c1`. Retained local/hosted proof includes 289 Tags tests, 44 renderer tests, 12 Node tests, 11 Python tests and shaded SQLite verification. CodeRabbit's skipped review is not an independent approval.

### Java uninterrupted timeout — PASS

At 19:46:43 UTC, FainNeito opened existing submission 81, page 0, then received no further input for 70 seconds. Captures show the map at 59.5 seconds and the original compass restored by 60.5 seconds. Before/after performance receipts reported approximately 20 TPS. These observations are consistent with the 1,200-tick implementation; screenshots and TPS samples are not an independent tick counter.

Authoritative Inventory SNBT, selected slot, Express database/WAL and configuration were unchanged. Submission 81 remained UNCLAIMED/AWAITING_REVIEW, revision 0 and delivery_pending 0. Roundtable request/claim 391/401 was explicitly released in SHARED 418.

### Available legacy artwork — PASS

At 20:07:33 UTC, existing submission 66 rendered map 0 and displayed “Map 1/1 · 64 copies”. Its record had no indexed museum tiles and retained a bounded 139-byte cargo payload. Explicit preview-close restored the compass at 20:08:16 UTC.

Inventory, complete database/WAL and config were unchanged; submission 66 remained CLAIMED/COLLECTED, revision 0 and delivery_pending 0. No cargo was claimed or redelivered. Roundtable 447/448 was explicitly released in SHARED 465. The separate submission 70/map 444 missing-data case remains valid evidence and was not fabricated or repaired.

### Approved sound audition — USER-CONFIRMED PASS

At 20:46:25/28/32/35 UTC, four MASTER-category cosmetic commands delivered the approved letter-send, letter-open, package-send and package-claim cues to FainNeito. Fresh server logs and an actual client screenshot show all four confirmations. The user subsequently said: **“The sounds work.”** Roundtable EXPRESS 556 / SHARED 557 records that human confirmation.

This establishes audition/listening acceptance. No recording was captured and no actual Express send/open/claim was performed. Inventory, complete database/WAL and config were unchanged; original FainNoir foreground was restored. SHARED 539 explicitly released the claim. The script execution policy was preserved.

### Java per-page lifetime — PASS on installed preview build

Under claim 600, FainNeito issued `/mail mapart preview 81 0` at **21:21:30.342570 UTC**, then `/mail mapart preview 81 1` at **21:22:19.244501 UTC**. The actual command gap was **48.901931 seconds**, rather than the planned 20 seconds, because the typed commands were separately inspected. The first page showed existing map 0 with two copies; the replacement showed map 14 with one copy. No further input was sent after the second command until expiry.

Command timestamps are not display-start timestamps. The first successful page-0 capture began at 21:21:30.750 UTC. For page 1, a chat frame was captured at 21:22:19.252–19.259 and the first successful map frame at 21:22:19.752–19.758. These frames bracket the visible transition, not the server's exact display callback. The command route interrupts the old preview before opening the new page.

| Observation | Capture began, UTC | Seconds after page-1 command |
| --- | --- | --- |
| Page 1 remains visible beyond page 0's old nominal deadline | 21:22:56.576504 | 37.332003; also 86.233934 seconds after the page-0 command |
| Latest map-visible checkpoint in the retained checkpoint log | 21:23:19.245158 | 60.000657 |
| First compass-restored checkpoint | 21:23:19.745098 | 60.500597 |
| Compass remains restored | 21:23:24.245077 | 65.000576 |

Before/after receipts report approximately 20 TPS. The observed lifetime is consistent with a fresh 1,200-server-tick session for the replacement page; screenshots, command-relative durations and TPS samples do not independently count ticks or establish an exact display-relative wall-clock deadline. The visual result establishes main-hand compass restoration; no distinct offhand fixture was demonstrated.

The retained silent **110-second, 220-frame, 2-fps** video covers the page switch and old deadline, not the later replacement-page expiry. Separate checkpoints record that expiry. The five-image review set includes a pre-expiry image from 21:23:18.746141 (the `new-59p5` checkpoint); the later map-visible observation at 21:23:19.245158 comes from the retained `new-60` checkpoint record. Fixed-rate video duration alone is not wall-clock timing proof. No audio or microphone was captured.

The acceptance record reports identical authoritative Inventory SNBT digest, selected slot 0, complete database/WAL/config hashes, mail 81, artwork 81 and tile rows. Submission 81 remained UNCLAIMED/AWAITING_REVIEW, revision 0 and delivery_pending 0; map IDs/copy counts stayed 0/2 and 14/1. The five imported screenshot sizes and SHA-256 hashes match the acceptance manifest. These before/after integrity comparisons are coordinator-recorded evidence; this documentation review did not repeat runtime reads. SHARED **612 released claim 600 at 21:25 UTC**. The JSON's 21:27:31 completion timestamp is evidence packaging, not the resource-release time.

This PASS applies to installed preview JAR `c6afbcf7`, not the uninstalled sound candidate or the new recovery patch. No actual mail workflow, candidate activation or persistent change was performed.

Transient “Suspicious Input” overlays observed in the timeout, legacy and per-page cases remain unexplained observations. They did not change the recorded integrity results; this ledger does not identify their cause.

## Conditional event-delivery defect and locally verified reconciliation

[TDD-073](tasks.md) / [REQ-067](requirements.md) records a source-proven conditional exposure, not an observed runtime incident. In `ddb27dc`, `EventDeliveryService` validates and packages before the repository can resolve a retry's existing token. After an earlier package commits but its reply is lost, a restart and stricter container policy can make the retry fail with `IllegalArgumentException`. Holidays candidate `97fb5dc7fdb9a35a6f0af30bab3d5f39529c23ca` treats that root exception as definitely not stored and clears its dispatch lock. The event insert also omits the configured `mail.max-package-payload-bytes` bound. Earlier TDD-047 / REQ-050 completion does not close these newly identified conditions.

The independently reviewed seven-file source/test patch reconciles validation, packaging and storage failures through the serialized repository queue: a matching stored token resolves its original mail ID, including after claim; proven absence preserves the original failure and classification; an unavailable lookup or conflicting recipient remains uncertain. Private in-flight ownership prevents a caller's cancellation or manual completion from settling the shared operation. Packaging remains on the caller's server thread, and the byte bound applies only to new tokens after canonical lookup. The public API and SQLite schema remain unchanged.

The former execution-pending state is superseded by actual native results: RED 20 tests/10 behavioral failures/zero errors or skips against unchanged `ddb27dc` production source, focused GREEN 30/zero failures/errors/skips, and full clean build 285/zero failures/errors/skips, plus EARS and 12 helper tests. The source is published, and all six hosted jobs and eleven API compilation targets have passed. The older 267-test result and hosted run remain attributed to the sound candidate. The recovery JAR is uninstalled, configuration is unapplied, and installed-build preview acceptance does not establish runtime adoption of this provider change. An uncertain outcome never authorizes a second physical copy.

## Remaining Java mail-flow acceptance

The approved profile has not been adopted by the running server. Express exposes no supported configuration reload command; editing disk files alone does not prove runtime adoption.

After the coordinator records the Staff recovery-gate disposition for these specific recipient/inventory actions and a new acknowledged TEST/client scope/window is obtained:

1. Verify active artifact and all twelve sound values, preserving `sounds.enabled` and unrelated config. Record inventory, balance, sender/recipient permissions, recipient presence, blocks/limits and one empty recipient storage slot.
2. With FainNoir offline, send one labelled signed book from FainNeito. Verify one new unread LETTER row and unchanged original book; hear `item.book.put` at 0.6/1.1.
3. Send one ordinary bundle/shulker containing a cheap labelled item. Record the actual quote before confirming once; verify one package and one exact postage charge; hear `block.wooden_button.click_on` at 0.6/1.0.
4. After FainNoir joins, left-click the exact new unread letter. Verify book display and unread=0; hear `item.book.page_turn` at 0.5/1.0. Right-click and Mark All Read do not exercise this cue.
5. Left-click the exact new package once. Verify one inventory delivery and `entity.item.pickup` at 0.5/1.0, then separately verify CLAIMED and delivery_pending=0 after asynchronous acknowledgment.
6. Close menus with empty cursors and retain both audit rows and expected balance/inventory deltas. Do not manually erase rows, refund or restore inventory to manufacture equality.

The cue can precede receipt acknowledgment. A held book-shaped tool icon does not prove an eligible signed-book fixture; museum donation 81 is not a normal package fixture.

## Remaining shared and release dependencies

- **SHARED-02:** complete, secure, consistent per-player recovery evidence remains technically unverified. Market 664 now reports the expected Staff code-source identity and storage ACTIVE after restart; this closes the earlier startup identity gap within the stated file-fingerprint limits, but does not establish complete restoration snapshots. Market retains host-request ownership. SHARED 641 records the reviewed draft as complete and unsubmitted, including missing-snapshot and bounded detached-session corrections; recovery clearance remains outstanding.
- **Authorized restart disposition:** Market 654 reports the owner's instruction, “Disregard the inventory issue and restart,” for one graceful TEST restart during 21:59–22:11 UTC, with Market as sole executor. **No Express JAR/config swap is included.** Market 664 reports the one restart completed, with server Done at 22:02:33 UTC and lifecycle/console release at 22:05:18 UTC. The 22:03:30–31 plugin receipt still identifies Express c6afbcf7. Market 668 separately holds FainNeito for its own testing through 22:25 UTC; Express has no live claim or queued action. Express activation requires fresh scope/claim; actual recipient workflow acceptance retains its specific gate disposition. The earlier universal restart hold does not describe this subsequently authorized action.
- **Holidays event packages:** the earlier TDD-047 / REQ-050 automated scope remains historical completed work. The TDD-073 / REQ-067 recovery change now has the independent review and native execution results above, with Express hosted verification now passing. Holidays PR 35 at `ba2fcf1471e5674e377b0df697f5dd87b63d20a6` is source-compatible according to independent review, draft and uninstalled. Holiday 657 reports canonical CI passing 327 tests in each of three Paper jobs and eleven API compilation targets. Independent review verified run `39503538581`, attempt 1, successful at 21:54:43 UTC: all three jobs passed, and logs contain eleven Paper compilation tasks plus `verifyPaperCompatibility`. Its checkout `59cf069ad25c93a335b4d14b641c2dee4dd3e17c` has tree `432007bc7f704eee26119b250ebef8922d518b7e`, matching the PR-head tree at `ba2fcf1`. The 327-tests-per-profile count remains attributed to the Holiday PR evidence; the report ZIP was unavailable to the reviewer, so no independent XML recount is claimed. SHARED 594 identifies the consumer as `ExpressGiftCourier`, using Express's public five-argument `EventDelivery.deliverPackage` method and deterministic event/giver tokens. Actual shulker serialization, provider pairing, handoff, delivery and recovery remain joint acceptance; canonical integration remains pending. The earlier SHARED 636 next-startup replacement and previously observed cff runtime are retained historical identities, not proof that PR 35 is running. Holiday's 593 resource-pack/client acceptance is separate from mail-provider acceptance.
- **Network release:** Tags source composition is complete. Any later merged release-network pin must select the reviewed combined source and preserve its renderer pairing. Neither inspected network tree has an Express gitlink. No network pin or canonical merge was performed.
- **Bedrock:** deferred by the user's current instruction. Existing sign-in and platform acceptance requirements remain documented; no Bedrock work is part of this continuation.

The current Roundtable API cannot set the required completion/review/validation flags. IN_REVIEW alone therefore does not prove unfinished implementation. TDD-073 now has independent review, published source and actual native RED/GREEN/full-build evidence; native and hosted verification are complete, while runtime acceptance retains its separate gates above. No completion gate is bypassed through database edits.

## Retained operational evidence

Private raw receipts and screenshots remain in the Entity workspace `roundtable-express-20261020`: `evidence/JAVA-TIMEOUT-ACCEPTANCE.json`, `evidence/JAVA-LEGACY66-ACCEPTANCE.json`, `runtime/sounds-20261020/configuration-manifest.json`, `four-cue-audition-result.json`, the user-confirmation receipts and `runtime/page-reset-20261020` checkpoints/video. The imported review set is `java-page-reset-evidence/JAVA-PAGE-RESET-ACCEPTANCE.json`, five hashed screenshots and `JAVA-PAGE-SWITCH-LIVE.mp4`. Original inventory payloads and database copies are not committed to this repository.

