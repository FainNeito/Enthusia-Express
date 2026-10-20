# EnthusiaExpress release readiness — 20 October 2026

## Scope and outcome

Roundtable task **EXPRESS-04** reviews the remaining release dependencies from Codex session `01a0722f-a583-7823-9304-6b8865eb8cab`. GitHub observations were refreshed on 20 October 2026 at approximately 18:30 UTC. This is a release and dependency review, not production approval or new client acceptance.

The inspected Express snapshot is commit `49dec87c2e03a1c28b27ae43df8bb66ee20ca5cc`, tree `0372d831acc8442408ca9ef44e2a86635c103d79`. Its transferred archive SHA-256 is `53612893cadd6c6b2702579a63e83a2562a6ed5de0b4364eec2968f5a921b248`; all 161 archived files match the local review copy. The copy intentionally contains no Git metadata. The coordinator owns remote application, integration, builds and commits.

**The actionable dependency gap is Tags composition:** the unique-mail release branch predates the merged Pumpkin Patcher additions. Existing green checks do not validate a combined artifact containing both. Express also retains explicit client acceptance gaps. None of these findings requires an immediate production change.

## Current review and verification evidence

| Component | Exact reviewed source | Current evidence | Remaining gate |
|---|---|---|---|
| [Express PR 12](https://github.com/FainNeito/Enthusia-Express/pull/12) | `49dec87c2e03a1c28b27ae43df8bb66ee20ca5cc`; base `a650216c6e795f900988b05d788de68b9d4a9866` | Open, mergeable, unmerged. [Verification run 39363284062](https://github.com/FainNeito/Enthusia-Express/actions/runs/39363284062) and Codacy succeed at this head. All six reported Paper/Java checks succeed. | Review and verify the final integrated continuation head. The present statuses cannot cover the new sound-default work before it is integrated. |
| [Canonical Tags PR 26](https://github.com/wsg138/EnthusiaTags/pull/26) | `111154b2e5c9420a2f9fe962906cb8d46ae973ef` | Open, mergeable, unmerged. [Verification 39196520117](https://github.com/wsg138/EnthusiaTags/actions/runs/39196520117), [artifact 39196520161](https://github.com/wsg138/EnthusiaTags/actions/runs/39196520161), Codacy and codacy-gate succeed. | Maintainer/source-lineage decision and merged-source verification remain separate from CI success. |
| [Tags release-fork PR 12](https://github.com/FainNeito/EnthusiaTags/pull/12) | `16a70aaddcea0c29db84b51961a57581e5ca63ac`; original base `6bbb2534cef121cce01223da0e536ea338fd6566` | Open, mergeable, unmerged. [Verification 39198432538](https://github.com/FainNeito/EnthusiaTags/actions/runs/39198432538), [artifact 39198432382](https://github.com/FainNeito/EnthusiaTags/actions/runs/39198432382), Codacy and codacy-gate succeed. | Reconcile with current fork main, preserve newer holiday assets/catalogue entries, then verify the resulting source and artifact. |
| [Tags current fork main](https://github.com/FainNeito/EnthusiaTags/tree/a2d7d9e1f2cd5e5732717a8bc35de56baf1be3da) | `a2d7d9e1f2cd5e5732717a8bc35de56baf1be3da` | Contains merged Pumpkin Patcher PR 13. Its current mail reader still uses row totals and lacks museum exclusion. | Deploying this main alone would not deliver the pending unique-correspondent change. |

The submitted-review endpoints returned **no reviews** for these three PRs. Express's combined status includes CodeRabbit success; that status is not evidence of an independent approval. This finding concerns recorded submissions, not whether informal review occurred elsewhere.

The Express checks are Paper 1.21/Java 21, 1.21.8/Java 21, 1.21.11/Java 21, 26.2/Java 25, 26.3-pre-2/Java 25 and 26.3/Java 25. The retained **267-test** result and eleven compilation targets are prior source/session evidence, not tests executed by this review worker. No builds or tests were run here.

## Unique-mail and holiday preservation

The canonical and fork PRs contain byte-identical `ExpressStatsReader.java`, Git blob `e40c5b04a0297a2b041ecc7236b34fe7e15531f9`:

- [Canonical reader at 111154b](https://github.com/wsg138/EnthusiaTags/blob/111154b2e5c9420a2f9fe962906cb8d46ae973ef/src/main/java/org/enthusia/tags/advancements/ExpressStatsReader.java).
- [Release-fork reader at 16a70aa](https://github.com/FainNeito/EnthusiaTags/blob/16a70aaddcea0c29db84b51961a57581e5ca63ac/src/main/java/org/enthusia/tags/advancements/ExpressStatsReader.java).

The reviewed changes count distinct case-normalized correspondents by mail type and direction, require available claim acknowledgment/read evidence, isolate returns, exclude self/system mail and omit rows identified by `mapart_submissions`. Existing ownership is retained; the reader does not create a lifetime ledger from expired provider history. The supplied tests exercise current/legacy schemas, repeated correspondents, museum exclusion and unchanged database bytes. These are consumer fixtures, not live provider/client acceptance.

[Current-main comparison](https://github.com/FainNeito/EnthusiaTags/compare/6bbb2534cef121cce01223da0e536ea338fd6566...a2d7d9e1f2cd5e5732717a8bc35de56baf1be3da) shows three commits after the PR's base, including the Pumpkin Patcher merge. PR 12 is one commit ahead and three behind current main. Preserve the new seed glyph/PNG, static tag and read-only source metadata, Halloween catalogue membership, `HolidayTagCatalog` entry and regression expectation. Earlier holiday menus, native advancement layout and renderer constraints must also survive. Do not replace this fork with an older canonical Tags artifact.

The release fork's [companion manifest](https://github.com/FainNeito/EnthusiaTags/blob/a2d7d9e1f2cd5e5732717a8bc35de56baf1be3da/tools/ci/companions.json) still selects renderer `01fac426152737c76cd4d2e570e07a33dde99798`, version `1.0.0-pilot.6-watcher-brackets-test.6`. A combined build must retain and verify this declared pairing. The historical 254-test canonical and 289-test unique-mail-fork counts do not establish that current-main composition passed.

## Network source and pin distinction

| Network source | Current main observed | Tags gitlink and repository | Renderer gitlink |
|---|---|---|---|
| [Canonical network](https://github.com/BadgersMC/enthusia-network/tree/559bfabc2187ab796a3be889f032383a8041f819) | `559bfabc2187ab796a3be889f032383a8041f819` | `28048ca64d460534fd8bac2d6295f7283b4aa27d`, `wsg138/EnthusiaTags` | `13acd15e406444ba2abec63de217ce2cc097396f` |
| [Owner's release-network fork](https://github.com/FainNeito/enthusia-network/tree/2280831cf899f63b7b82dacbf42bf72bee37239e) | `2280831cf899f63b7b82dacbf42bf72bee37239e` | `6bbb2534cef121cce01223da0e536ea338fd6566`, `FainNeito/EnthusiaTags` | `01fac426152737c76cd4d2e570e07a33dde99798` |

These are different release sources. The [fork's retained release record](https://github.com/FainNeito/enthusia-network/blob/2280831cf899f63b7b82dacbf42bf72bee37239e/docs/holiday-release-20261017.md) documents owner approval to use the merged Tags fork after the canonical merge was unavailable. It does not claim an upstream merge or authorize this worker to merge or change pins.

Both complete Git trees lack Express and Holidays gitlinks. **There is no existing Express network pin to bump in either inspected tree.** Track their standalone source/build/artifact provenance separately unless the coordinator explicitly scopes new network integration. The release fork's Tags pin also predates Pumpkin Patcher and unique mail. A future pin PR needs the actual reviewed, merged composition and its combined build; changing only the canonical Tags pin would not preserve the established release fork's complete holiday presentation.

The retained network document lists historical standalone Holidays `d07390924de3a953a0685958325de6cf24e85d8d` and UAA `94b59a461bcdd0452b98dbdf3536a678b77839ac`. These are dated evidence. The current Holidays branch query returned 404 through this GitHub connection, so its current head/artifact remains unverified here. Obtain that identity from the active Holiday coordinator before a combined release; do not substitute the historical pin.

Separately attributed update: the Holiday coordinator's SHARED `resource-coordination` message, sequence 178 at 2026-10-20 18:27:19 UTC, reports Holiday PR 34 at `6d3747b3efbac3a24de080f87b4e0a324aba8103`, all three hosted matrices/all eleven profiles passing with 310 tests each, and testing JAR SHA-256 `27a257e6ffe7d5b40ed97f4ac8b4db37c31da8e581a86d0e09e5b54f3e8cbe89`. That testing artifact is reported retained on Entity and not installed; no shared resource is held, and HOLIDAY-05/06 remain BACKLOG. This is coordinator-reported evidence, not an independent GitHub/API or artifact verification by this worker. Current deployed Holiday identity remains unverified. Independently confirm the source/dependency pairing and artifact during coordinated acceptance.

## Acceptance evidence and remaining gates

The recovered session supersedes older broad “pending” statements in `docs/tasks.md`: Java museum workflows, recipient behavior, ordinary-player permission checks, the four old sound captures and CombatLogX rejection have later acceptance evidence. Java preview rendering, page navigation, Close/sneak/combat restoration and unchanged inventory have evidence through the session recorded at 03:15 UTC on 20 October. This review carries those results forward without claiming a retest.

The remaining acceptance matrix must distinguish:

| Gate | Evidence required to close it |
|---|---|
| New sound defaults | Final source/config assertions and a separately coordinated real-client audibility check. Old sound captures do not prove the new requested cues. |
| Real Bedrock | Actual supported Bedrock/Floodgate client evidence for forms, fallback and preview controls. Java and simulations do not close it. |
| Preview timeout | Actual 1,200-server-tick per-page expiry and hand restoration, with TPS/wall-clock time recorded; see [preview readiness](roundtable-preview-readiness-20261020.md). Automated timeout coverage remains separate. |
| Legacy artwork with saved pixels | A pre-existing valid legacy donation and available saved map pixels. The Eve/submission 70 references missing map 444 on TEST and cannot prove successful rendering. Do not recreate it. |
| Final combined source | Coordinator's full Express suite, architecture/helper checks and current-head hosted checks after integration; preserve exact artifact hashes. |
| Tags/network composition | Reconciled merged Tags source retaining Pumpkin Patcher and prior holiday menus, unchanged companion constraints, appropriate release-network pins and combined validation. |
| Holidays integration | Independently confirm the coordinator-reported source/dependency pairing and artifact during coordinated acceptance; verify the matching Express dependency and deployed identity if the release includes Secret Santa/event mail. |

Express's [EventDelivery API](../src/main/kotlin/io/enthusia/express/api/EventDelivery.kt) remains `deliverPackage(UUID, String, String, List<ItemStack>, UUID): CompletableFuture<Long>`. Its contract requires a server-thread call, caller ownership until successful completion and one package per stable token, rejecting token reuse for another recipient. Existing event-service/repository tests and REQ-050 support the API contract; `docs/tasks.md` explicitly leaves real shulker serialization/delivery acceptance with Holidays separate. No event, payout or live mail was performed by this review.

Shared TEST, desktop and client use remains coordinator-owned and requires the current acknowledged resource window. This document claims no reservation. It changes no source implementation, Tags/Holidays/network repository, GitHub state, credentials, server, player, inventory or production artifact.
