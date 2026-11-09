# Museum review and hybrid Express menus

This is an unmerged 1.3.0-SNAPSHOT testing change, based on Express main a650216. It is not production acceptance or a production release.

## Artist workflow

`/mail` opens navigation. `/mail inbox` and `/mail sent` retain their existing chest interfaces; signed-book letters still use the existing book reader. `/mail mapart submit` collects artwork title, credited artist, width/height and optional description before opening the existing physical cargo inventory. The submitting account is retained separately from artist credit. Dimensions are 1–32 maps per side and must match the donated map quantity. The manifest records every map ID and stack quantity; it does not infer the artwork's tile placement order or render the map pixels.

The cargo slot retains its protected gray marker. Postage is quoted before confirmation; changing the cargo or price requires another quote. Duplicate map IDs are checked before payment and again atomically during admission. An authorized sender may explicitly request a duplicate override by typing `CONFIRM` in the input or using `/mail mapart override` with the same descriptor. That requires `enthusiaexpress.mapart.duplicates` and is rechecked before and after payment callbacks. Map ID reuse is a duplicate warning, not proof of copyright or artist ownership.

`/mail mapart mine` shows only the sender's museum submissions, separately from ordinary sent mail. Entries include credited artist, donor, dimensions, curator, feedback, bounded review history and a paginated complete map manifest. Metadata can be corrected after a request for changes, without withdrawing, replacing or duplicating the stored physical cargo.

Fallback text input is a clickable suggestion: `/mail mapart draft title|artist|2x3|description`. The `|` delimiter is reserved for these fallback commands. Existing typed commands, permissions, inboxes and physical package/book serialization remain supported.

## Curator workflow

`/mail mapart queue` or `inbox` opens the manager queue. `processed` selects displayed artwork. `search <text>`, `assigned`, `unassigned`, and the filter menu provide bounded title/artist, status and curator filtering. Search text is bound as data, not an SQL pattern. Curators can assign an entry to themselves; reassignment therefore records the acting curator's own identity rather than taking an arbitrary unverified name.

New entries progress through Awaiting review, Accepted, Collected and Displayed. Needs changes holds the original cargo and records feedback; only the submitter can correct its metadata and resubmit. Review and assignment updates use an observed revision, so stale clicks cannot overwrite another curator's work. Acceptance is required before claiming new submissions. Collecting uses the existing claim-generation, movement-lease and delivery-receipt recovery path. A pending delivery acknowledgment cannot be marked displayed, and a repeated Collect action never means display approval.

Durable review history is separate from recipient notifications. Accepted, needs-changes, collected and displayed updates are delivered to online artists or on a later connection. Notices are acknowledged only after the send callback succeeds. Crash/retry delivery is at least once, so a notification can repeat; physical items are never transferred by the notification path. Acknowledging a notification does not delete review history.

## Interfaces and compatibility

`gui.dialogs.enabled` enables modern Java dialogs; older Java clients and unavailable APIs retain chest/command fallbacks. The optional ViaVersion API is consulted for the original client protocol. Bedrock players use optional Floodgate/Cumulus forms when installed on this backend; Java dialog translation is not assumed. If a detected Floodgate player cannot receive a form, the same chest/command fallback is retained. Reliable Bedrock detection requires Floodgate on this backend; a proxy-only installation cannot be identified from Java protocol numbers alone. Keep gui.dialogs.enabled disabled on that configuration until backend routing is verified. The adapters use runtime public API discovery and do not bundle Paper, ViaVersion, Geyser or Floodgate.

Dialogs are navigation and text input, never a substitute for cargo slots. Deposited items remain owned by ShippingService; no dialog is opened over a charged or deposited shipment. Public menu callbacks are owner-bound, single-use and expiring, return to the server thread and revalidate live permissions/combat/session before mutation. Closing an input form submits nothing.

`notifications.toast.mode` supports `native`, `actionbar` and `chat`. `single-message` supports `{sender}`, `{type}`, `{count}`; `group-message` supports the same tokens. Native mode still uses the client-owned advancement heading. Action-bar mode can show a mail-only message but is not a top-right toast. PacketEvents is not required. Notification modes are server configuration, not per-player preferences.

Nexo chest fallbacks use the existing optional theme resolver, including `gui.nexo.titles.museum` and `gui.nexo.icons.museum.control`. Actual custom-item, glyph and resource-pack rendering requires client acceptance.

## Migration, rollback and release gates

The additive `museum_artworks`, `museum_tiles`, `museum_history` and `museum_updates` tables retain existing mail rows and cargo. Legacy unclaimed museum entries remain accepted, preserving existing curator access. Existing delivery and processed evidence determines collected/displayed state. Missing historical map manifests or review decisions are not invented: legacy entries retain their known first map ID and no fabricated prior audit trail. Legacy color codes are normalized only in catalog presentation; physical payloads are unchanged.

Back up the full plugin directory, receipt/recovery journals, player inventory and currency data as one coordinated recovery set before any separately authorized deployment. Do not simply downgrade against new review data: older code does not enforce acceptance or duplicate admission. Rehearse rollback with the coordinated backup. Inventory, currency and SQLite remain separate stores; ambiguous crash outcomes still require administrator reconciliation as documented in production-safety.md.

Canonical Tags PR #26 supplies unique personal-mail correspondents and excludes museum donations. That PR must be merged by a maintainer, then the network's applicable Tags source/pin must be reconciled and its combined build verified. The deployed fork also contains newer holiday/menu changes; replacing it with an older canonical Tags artifact would regress those features. No pin or artifact should be represented as production-ready until this reconciliation is reviewed.

## Preview and acceptance

[Mobile-accessible interactive preview](https://express-museum-dialogs-preview-20261018.awareyak.chatgpt.site) (owner-private, sign in as the owner). This is an illustrative simulation, not screenshots of a Minecraft client. It covers submission, physical cargo, owner history, review feedback, revision conflicts and acting-curator assignment. Notification controls simulate server-wide configuration, and the manifest placeholder does not render map artwork or infer tile order. Java/Bedrock/chest layouts illustrate equivalent flows rather than exact client rendering. Browser availability prevented fresh in-app verification of the hosted URL in this session; Sites reports successful deployment. Real device and in-game acceptance remain pending.

Remaining acceptance must use actual configured EnthusiaCurrency/Vault, CombatLogX, Nexo/resource pack and Geyser/Floodgate versions. Your prior CombatLogX confirmation is retained, but is not fresh acceptance of every new workflow. Verify bank-only/physical/mixed postage, duplicate races and refund compensation, callback disconnect/combat/revoked permission, full inventories, receipt replay, original map preservation, curator conflicts, client menu transitions, input escape/cancel and bounded lists. Keep chat filtering deferred. No live server mutation is authorized by these checks.
