# Museum preview acceptance readiness — 2026-10-20

## Current acceptance status

See the [Java acceptance ledger](roundtable-java-acceptance-20261020.md) for exact installed-build identity and dated evidence.

| Case | Current status |
| --- | --- |
| Java uninterrupted lifetime and restoration | PASS, 19:46:43 UTC start; unchanged inventory/database/configuration. |
| Existing legacy artwork with saved pixels | PASS, submission 66 at 20:07:33 UTC; map 0, 64 copies, unchanged claim/review state. |
| Per-page lifetime reset | PASS on installed `c6afbcf7`; commands 48.901931 seconds apart, correct second page survives the old nominal deadline and later expires with the compass restored. Inventory/database/configuration and submission state unchanged; SHARED 612 released claim 600 at 21:25 UTC. |
| Missing legacy pixels | Submission 70/map 444 remains a separate safe-unavailable case; no replacement pixels were created. |
| Bedrock | Deferred by the user's current instruction. |

The per-page command times were 21:21:30.342570 and 21:22:19.244501 UTC. First successful captured displays began at 21:21:30.750 and 21:22:19.752 UTC; they are observations, not exact server display callbacks. The checkpoint log records the second map at 21:23:19.245158 UTC and the restored compass at 21:23:19.745098 UTC, respectively 60.000657 and 60.500597 seconds after the second command. Approximately 20 TPS is consistent with the 1,200-tick implementation; no independent tick count or exact wall-clock deadline is claimed. The silent 110-second video covers the page switch and old deadline only; separate checkpoints establish the later expiry. The ledger records the full timing and integrity limits, including visual confirmation of the main-hand compass without a distinct offhand fixture.

The source analysis and procedures below are retained. Their original pending observations refer to the earlier snapshot; use the current status table above.

## Scope and disposition

Roundtable EXPRESS-03 inspected the tracked source snapshot at
`49dec87c2e03a1c28b27ae43df8bb66ee20ca5cc`, tree
`0372d831acc8442408ca9ef44e2a86635c103d79`, against REQ-064 and REQ-065.
No actionable preview defect was established by this source review. This
contribution changes only this document; it adds no duplicate regression tests,
runtime changes, dependencies, permissions or new design.

The existing 13 `MuseumPreviewTest` cases already include simulated timeout,
permission/combat restoration, interrupted and superseded requests, owner scope,
non-collection, map/page bounds and bounded legacy decoding. They were inspected,
not rerun by this worker. The recorded 267-test baseline and hosted workflow
39363284062 are coordinator-recovered evidence; the coordinator owns fresh
combined verification. A passing mock timeout does not establish client
restoration on either platform.

## Source-backed behavior

| Area | Inspected behavior | Evidence |
| --- | --- | --- |
| Access | Current player object, online/alive state, plugin state, `enthusiaexpress.use` and combat are checked before lookup/display. The viewer must own the submission or have museum management permission. Display repeats access checks after closing the existing inventory. | `MuseumMenus.allowed`, `MuseumMenus.preview`, `MuseumPreview.show`, `MuseumPreview.canView`; owner/permission regressions in `MuseumPreviewTest`. |
| Ownership | Preview uses self-only equipment changes and an existing map. It never inserts a map into the authoritative inventory, claims cargo, changes review state or charges postage. | `MuseumPreview.show`; `reviewerSeesExistingMapWithoutClaimingOrChangingInventory`. |
| Timeout | Each successfully displayed page starts a 1,200-server-tick lifetime. At normal 20 TPS this is approximately 60 seconds. A new page starts a fresh lifetime; this is not a wall-clock deadline independent of server lag. Expiry removes the session and refreshes the current online player's inventory display. | `PREVIEW_TICKS`, `MuseumPreview.tick`, `MuseumPreview.close`; `permissionLossCombatAndExpiryRestoreTheClientDisplay`. |
| Delayed results | A per-player request token and the original top-inventory identity prevent a stale artwork lookup from replacing newer navigation. Completion rechecks current access and artwork visibility. | `MuseumMenus.preview`; `delayedResultsRecheckOwnershipAndNewestRequest`, `newerNavigationWinsOverAnEarlierDelayedPreview`, `disconnectAndNewInventoryInvalidatePendingPreviewResults`. |
| Interruption | Commands, inventory opening, disconnect, death, teleport, held-slot changes and sneaking invalidate preview work. Use, attacks, dropping, swapping, inventory clicks and drags end an active preview and cancel that first hidden-item interaction. Plugin shutdown also closes active previews. | `MuseumPreviewEvents`, `MuseumPreviewInteractions`, `MuseumMenus.shutdown`; both listener registrations and shutdown wiring in `EnthusiaExpressPlugin`; existing interaction/exit regressions. |
| Legacy cargo | Indexed map IDs avoid decoding. A legacy submission without indexed tiles derives its manifest from size-bounded retained cargo using the existing scanner. It does not persist a new manifest or rewrite the donation. Missing map data is rejected without creating a replacement map. | `MuseumPreviewTiles.read`, `MuseumPreview.show`; the three legacy tests and `missingMapsAndInvalidPageNeverCreateReplacementMaps`. |
| Bedrock menus | Backend Floodgate identity selects Cumulus simple/custom forms; unavailable dialogs return to the existing chest presentation. Bedrock viewers do not receive the Java Nexo Post Office skin. Form callbacks return to the server thread and use owner-bound, single-use session tokens. | `DialogMenus.present`, `DialogMenus.bedrock`, `DialogSessions`, `MuseumView.open`, `GuiTheme.available` and `GuiTheme.bedrockViewer`. |
| Bedrock preview | The preview itself has no separate Bedrock renderer. It sends the same Paper cosmetic equipment/map operations and chat command links used for Java. Actual Geyser rendering, hand restoration and usable controls remain unproven. | `MuseumPreview.show` and `MuseumPreview.controls`; no Bedrock rendering claim is derived from the Java or form tests. |

All file names above refer to the existing source under
`src/main/kotlin/io/enthusia/express` and tests under
`src/test/java/io/enthusia/express`. No new imports or architectural boundaries
are introduced by this documentation-only contribution. SPEAR global task/state
updates and the final arch/refine gate remain coordinator-owned.

## Existing client evidence

The requested Codex session records successful Java artwork rendering, Next and
Previous, details/Close, sneak and actual CombatLogX interruption, unchanged
authoritative inventory digests and unclaimed submission state. Its last summary
was at 2026-10-20 03:15 UTC. The earlier Java evidence and exact TEST artifact
limits are retained in `museum-preview.md`.

That session did not exercise Bedrock, the uninterrupted full preview lifetime,
or successful legacy artwork rendering with available saved pixels. Legacy
submission 70, The Eve, references map 444; its saved map data was absent on TEST.
Its safe unavailable message is valid missing-data evidence, not a successful
legacy rendering result. Do not generate replacement pixels, alter the donation,
or reuse that case as a successful-rendering fixture.

## Procedure matrix — apply current statuses above

The coordinator must reserve the shared TEST server and client before running
these cases. This document performs or authorizes no server/client action. Record
the actual activated commit/JAR identity and Paper, client, Geyser and Floodgate
versions; do not infer them from the reviewed source snapshot. Use existing
labelled QA submissions where possible and record their IDs, map IDs and expected
copy counts. Preserve the player's initial hand/offhand, inventory digest,
location and any temporary test permissions.

| Case | Procedure | Required evidence and pass condition |
| --- | --- | --- |
| Java uninterrupted expiry | Open an existing populated QA map. Record start time and server TPS, then leave the client idle without paging, commands, inventory use, sneaking or a held-slot change. Observe through 1,200 server ticks. | Before/display/expiry captures show the real main-hand and offhand restored automatically. Record wall-clock duration together with TPS; lag must not be mistaken for a 60-second wall-clock guarantee. Inventory digest, cargo claim state and review state remain unchanged. |
| Per-page lifetime | Open page 1; before it expires, select page 2. Remain idle for the new page's full lifetime. | The new page displays the correct existing map and copy count, then expires after its own 1,200 ticks. No old page reappears. This is a targeted timing check, not another full Java rendering pass. |
| Bedrock navigation | With a real backend Floodgate player, open Express, My submissions and an authorized artwork detail screen. Open Preview artwork. | Record the actual form/chest route, readable controls and absence of the Java-only Nexo skin. The player's own submission is accessible; an unrelated submission remains inaccessible without management permission. |
| Bedrock map and controls | View a populated, multi-map QA submission. Exercise Next, Previous, details, Close and sneak using the actual device controls. | Capture real pixels, correct page/copy counts and restoration. Separately record whether chat links work on that Bedrock device. If they do not, inspect the existing typed-command route below and report the UX limitation explicitly; successful forms alone are not preview acceptance. |
| Bedrock expiry and interaction | Repeat the uninterrupted expiry case. In a separate run, try a normal use/drop or swap interaction while previewing with a known real item originally in hand. | Both hands restore after expiry. The first protected interaction ends preview without using/dropping/swapping the hidden real item. The inventory digest and submission ownership remain unchanged. Do not use valuable items for this check. |
| Legacy artwork with saved pixels | Select an existing authorized legacy submission with no indexed manifest, valid retained map cargo and confirmed available saved map data. Read-only verification must establish the fixture before viewing it. | Actual pixels and correct unique-map/copy counts render; cargo bytes, review/claim state and inventory digest remain unchanged. If no suitable fixture exists, leave this case BLOCKED with that reason. Do not alter The Eve or fabricate a successful fixture. |
| Legacy unavailable data | Retain the existing The Eve/map 444 result, or inspect it only if a new artifact change requires a regression check. | An unavailable message leaves cargo and inventory unchanged and creates no map. This remains separate from the valid-legacy rendering case. |

Existing command routes, if direct entry is needed during Bedrock diagnosis:

```text
/mail mapart view <submission-id>
/mail mapart preview <submission-id> <zero-based-page>
/mail mapart preview-close
```

Command execution interrupts the currently displayed preview before opening any
new requested page. Do not send diagnostic commands during an uninterrupted
expiry measurement. Keep command support, touch/controller usability and actual
map rendering as separate observations.

## Closure criteria

The coordinator can close the readiness-document task after incorporating this
file and its existing evidence into the combined review. Java uninterrupted
expiry, per-page lifetime reset and available legacy rendering now have dated
PASS evidence on the installed preview build. Bedrock is deferred by the user and
remains unaccepted for the broader platform scope. A failed real
client case should first be reduced to a concrete defect before starting a new
SPEAR source change. No additional source test run is requested solely for this
document. The coordinator's final native build for `e7bc6109e66a89c08f40c9e3aa6bbe48bb1322f1`
passed 285 tests with zero failures/errors/skips at 21:55:22.406465 UTC, with
EARS and all 12 SPEAR helper tests passing. The separate TDD-073 / REQ-067
recovery work has actual RED evidence on unchanged `ddb27dc` production source
(20 tests, 10 behavioral failures, zero errors/skips) and focused GREEN evidence
(30 tests, zero failures/errors/skips). The earlier 267-test result and hosted
checks remain attributed to `ddb27dc`. The published recovery source and actual
native suites are recorded in the [recovery evidence](evidence/event-delivery-recovery-20261020.json).
At the coordinator's 22:05:42 UTC verification, recovery run 39505007021 had
completed all six jobs successfully, including all eleven API compilation
targets; the baseline clean-build step passed (the exact 285-test XML count above is from the native run). Exact-commit Codacy
check 118912063119 passed with zero annotations.
The recovery candidate remains uninstalled and the sound config unapplied.
Preview acceptance applies to the installed build and does not establish
candidate activation or joint actual-provider acceptance. See the current
[Java acceptance ledger](roundtable-java-acceptance-20261020.md) for the retained
Staff recovery, Holidays integration and release gates.

