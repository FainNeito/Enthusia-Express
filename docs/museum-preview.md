# Museum artwork preview

The artwork detail screen has a **Preview artwork** action. An artist can preview their own submissions; a player with `enthusiaexpress.use` and `enthusiaexpress.mapart.manage` can preview the curator queue's submissions. CombatLogX access checks still apply. No new permission or dependency is required.

The viewer receives an existing filled map through a private cosmetic hand display. The original cargo stays unclaimed and the real main-hand/offhand items are never replaced. Clickable chat controls provide Previous, Next, Artwork details and Close preview. Sneaking closes the preview. A page expires after 60 seconds, and inventory use, held-slot changes, dropping/swapping/using items, teleport, disconnect, death, permission loss or combat end the display. An interaction that would use a hidden held item is cancelled on exit.

Each unique map ID has one page, with its submitted copy count. Pages sort by map ID. Existing museum metadata records dimensions and copy counts but no tile coordinates, so this feature does not invent a stitched layout. Missing map data reports an unavailable preview without generating a replacement map. Preview does not charge postage, mutate review state or claim items. Indexed submissions need no cargo decoding. For older donations without an indexed manifest, the selected authorized preview decodes a bounded retained payload and derives tile IDs/copies using the existing map-only container scanner, without persistence or inventory changes. Legacy details identify missing indexed information instead of claiming zero physical maps. Look down to see the full vanilla held-map display.

## Authorized Java client findings

The c5fda79 TEST run on Paper26.2/Dawn26.3 rendered two populated maps, including actual artwork, with next-page navigation and correct2/1 copy counts. The authoritative inventory digest matched the pre-preview inventory while the artwork was displayed, and sneaking restored the original compass. The original QA map18 was blank in saved data, not a rendering failure. A labelled three-map QA fixture81 required the explicit existing duplicate override for map0 and cost1 TEST currency; it remains separate from player deliveries.

This run also reproduced missing legacy tile indexes for The Eve. `legacy-preview-red.log` records two genuine failures before the fallback; thirteen focused preview tests and the full267-test/all-eleven-API build pass after the bounded fallback (`legacy-preview-green.log`, `legacy-preview-refine.log`). Cosmetic viewing guidance and legacy labels retain the full267-test pass in `legacy-preview-final.log`. Final legacy client acceptance is recorded separately after the updated TEST activation; the source fix alone does not establish it.

The temporary display uses Paper's `sendEquipmentChange`, `sendMap` and `updateInventory` APIs. It creates no world entities or new MapViews and adds no PacketEvents/Nexo dependency. Java rendering, interruption timing, Geyser translation and Bedrock controls still require separately authorized real-client acceptance. Public API calls and mocked tests do not prove their final client presentation.

[Interactive phone-width-checked simulation](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site/map-preview.html) uses illustrative artwork and does not connect to the server. It is owner-private. No TEST or production operation is authorized by this feature work.

## Local proof

- `map-preview-red.log`: 5 of 7 initial tests failed before implementation because no preview route/display existed.
- `map-preview-green.log`: the 7 initial tests passed after implementation.
- `map-preview-refine.log`: full build and all eleven Paper1.21 API compilation targets passed; 264 tests, zero failures/errors/skips. Architecture and compiled call-cycle checks are included.
- `MuseumPreviewTest`: 10 focused cases cover reviewer/owner privacy, page bounds, unique IDs/copy counts, unavailable maps, no inventory/cargo changes, stale requests, permission loss/combat/timeout, explicit exit, interaction cancellation and interrupted/disconnected sessions.
- Project EARS validator and twelve SPEAR helper tests passed.
- `map-preview-26.3.log`: a clean pinned final Paper26.3/Java25 build tested the baseline Java21 JAR with the same 264 passing tests. Baseline JAR SHA-256: `5dcef4e3bde336ed47dafd75b08269aad1b1c052f3bc8005888cb11b530f3a2b` (unmerged/local verification artifact).
- `map-preview-reviewed.log`: after the three initial Codacy findings, lifecycle and interaction adapters are separated and display failures clean up through `finally` before MainThread logs them. The full264-test build and all eleven API compilation targets pass again. Hosted checks must be read for the final source head.

These logs are local evidence, separate from exact-head hosted checks and client acceptance. No server upload, reload, restart or activation was performed.

## Authorized Java client validation — 2026-10-19

User authorized TEST deployment and their client. Source3a4b45e was activated on SMP Test (Paper26.2-128, Java25, Dawn26.3) with the installed Currency1.4.4, CombatLogX11.6.0.0.1286 and Nexo1.29.1. The subsequent64e5cdc test-import-only build has the identical distributable SHA256 c6afbcf7a4d5572834f9b8a639b833dfa3aef46fb610dfc14e7b115fa78d5197. Offline coherent backups precede both TEST updates. Authenticated upload size and17 extracted relevant archive entries matched; expired storage TLS prevented full remote-JAR SHA verification and was not bypassed.

A labelled bundle submission81 contains map0 twice and map14 once. Actual client rendering, two unique pages with2/1 copy counts, Next, Previous, Artwork details, explicit Close, sneak exit and real CombatLogX interruption passed. Server Inventory NBT digests match before/during paging/after combat/final cleanup. The submission remains awaiting review and unclaimed; postage was quoted and charged once. No config or permission changes were made. No Express error was found in the post-start log; one root JAR and an empty update folder were verified.

Legacy submission70 (The Eve) safely reports unavailable. Read-only diagnostic copies show its371-byte retained filled-map payload references map444; world/data/map_444.dat is absent on TEST. This is not accepted as successful legacy artwork rendering. Do not invent a new map or modify the donation to mask missing world data. Legacy fallback with valid cargo is covered by automated tests; actual legacy rendering with available saved pixels remains pending.

Bedrock,60-second timeout and recipient-side delivery were not exercised in this viewer session. The client's initial location/camera were restored and its combat tag removed. Existing resource-pack download and staff handoff warnings are independent limitations. Production and monorepo pins remain unchanged; PR12 is unmerged.

[Private interactive preview and actual Java captures](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site/map-preview.html). Illustration and client evidence are explicitly separated.
