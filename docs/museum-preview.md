# Museum artwork preview

The artwork detail screen has a **Preview artwork** action. An artist can preview their own submissions; a player with `enthusiaexpress.use` and `enthusiaexpress.mapart.manage` can preview the curator queue's submissions. CombatLogX access checks still apply. No new permission or dependency is required.

The viewer receives an existing filled map through a private cosmetic hand display. The original cargo stays unclaimed and the real main-hand/offhand items are never replaced. Clickable chat controls provide Previous, Next, Artwork details and Close preview. Sneaking closes the preview. A page expires after 60 seconds, and inventory use, held-slot changes, dropping/swapping/using items, teleport, disconnect, death, permission loss or combat end the display. An interaction that would use a hidden held item is cancelled on exit.

Each unique map ID has one page, with its submitted copy count. Pages sort by map ID. Existing museum metadata records dimensions and copy counts but no tile coordinates, so this feature does not invent a stitched layout. Missing map data reports an unavailable preview without generating a replacement map. Preview does not charge postage, mutate review state, deserialize cargo or claim items.

The temporary display uses Paper's `sendEquipmentChange`, `sendMap` and `updateInventory` APIs. It creates no world entities or new MapViews and adds no PacketEvents/Nexo dependency. Java rendering, interruption timing, Geyser translation and Bedrock controls still require separately authorized real-client acceptance. Public API calls and mocked tests do not prove their final client presentation.

[Interactive phone-width-checked simulation](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site/map-preview.html) uses illustrative artwork and does not connect to the server. It is owner-private. No TEST or production operation is authorized by this feature work.

## Local proof

- `map-preview-red.log`: 5 of 7 initial tests failed before implementation because no preview route/display existed.
- `map-preview-green.log`: the 7 initial tests passed after implementation.
- `map-preview-refine.log`: full build and all eleven Paper1.21 API compilation targets passed; 264 tests, zero failures/errors/skips. Architecture and compiled call-cycle checks are included.
- `MuseumPreviewTest`: 10 focused cases cover reviewer/owner privacy, page bounds, unique IDs/copy counts, unavailable maps, no inventory/cargo changes, stale requests, permission loss/combat/timeout, explicit exit, interaction cancellation and interrupted/disconnected sessions.
- Project EARS validator and twelve SPEAR helper tests passed.
- `map-preview-26.3.log`: a clean pinned final Paper26.3/Java25 build tested the baseline Java21 JAR with the same 264 passing tests. Baseline JAR SHA-256: `5dcef4e3bde336ed47dafd75b08269aad1b1c052f3bc8005888cb11b530f3a2b` (unmerged/local verification artifact).

These logs are local evidence, separate from exact-head hosted checks and client acceptance. No server upload, reload, restart or activation was performed.
