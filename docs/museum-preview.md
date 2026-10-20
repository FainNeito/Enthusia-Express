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
