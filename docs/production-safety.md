# Production safety changes in 1.2.1

This release addresses the five findings in the review of main commit `66786088c3988478afe34518d779694b00d882b1`. Automated tests do not constitute staging approval. Deploy first with disposable accounts and balances on the exact production plugin stack.

## Ownership and payment

Shipping removes cargo from its GUI slot and marks the player as sending **before** invoking any payment provider. Reentrant confirmations are rejected. If a synchronous currency listener closes the menu, changes eligibility or brings the recipient online, the send is cancelled and its reserved cargo and successful payment are compensated once. A rejected payment restores cargo to the still-owned empty slot or returns it to the current player if the menu has closed.

Database rejection, including queue admission failure, follows the existing cargo and original-provider fee refund path. A failed provider refund is reported for administrator reconciliation. An exception from a provider does not prove whether funds were debited; do not manually refund until that account's transaction history is checked.

## Recovery and migration

Initialization adds `claim_generation INTEGER NOT NULL DEFAULT 0` without modifying existing payloads. Claims compare their observed generation and status. Restoring an undelivered claim atomically advances the generation and restores the original expiration timestamp. A stale snapshot cannot reserve or restore a later attempt.

The delivery worker stores known-undelivered compensation in `delivery-receipts/undelivered/<id>-<generation>.restore`, forces its content to disk, then attempts SQLite restoration. Database failures are retried every five seconds and during graceful shutdown. Receipts replay after restart. An old receipt left behind after successful restoration cannot affect a newer generation.

Delivered `.ack` files retain their separate meaning: they release reservations and never return items. Unknown pending claims are not automatically restored. Invalid or conflicting recovery metadata is retained and logged for inspection. If the receipt filesystem itself is unwritable, the worker retains the intent in memory and retries; a process crash before that intent reaches disk remains uncertain.

Before upgrading, stop the server and back up **the entire plugin data folder**, including receipts, plus player inventories and currency data. Keep the backup as one recovery set. Do not downgrade to 1.2.0 against the migrated live database: old code does not enforce generation guards. Restore a coordinated backup if rollback is necessary.

## Resource budgets

Existing configuration files receive these defaults through code; new installations also show the settings in `config.yml`.

| Setting | Default | Purpose |
| --- | --- | --- |
| `mail.max-package-payload-bytes` | 262144 | Reject new oversized packages before payment; valid range 1024–1048576 |
| `database.max-queued-operations` | 256 | Bound queued SQLite work; rejected submissions complete exceptionally instead of disappearing |
| `mail.max-completions-per-tick` | 64 | Limit completion callbacks started in one tick |
| `mail.completion-budget-ms` | 2 | Stop starting callbacks after the tick budget is exhausted |

Container scanning also has a fixed 8192-step traversal budget, in addition to the configured nesting limit. The callback time budget cannot preempt an individual third-party callback already running.

Mailbox and sent-history page queries omit all payload BLOBs. Package icons are lightweight chest summaries rather than containers carrying full nested item metadata. Books and packages are fetched individually when selected. Oversized legacy cargo remains stored, but normal claiming refuses to decode it above the configured limit; contact an administrator to arrange controlled recovery. Viewing sent history never transfers items or changes recipient unread state.

Navigation retains one active query per player and the latest requested view. A scheduler tick starts that view once the previous query completes. Closing a menu discards its pending navigation. Shutdown drains accepted writes and callbacks before closing SQLite; do not force-kill a server that is still completing compensation.

SQLite JDBC is upgraded to **3.51.3.0**, including the WAL-reset corruption fix. Sources: [SQLite WAL advisory](https://sqlite.org/wal.html), [Xerial release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.51.3.0). Keep the database on a local filesystem and avoid concurrent writable maintenance while the server is running.

## Administrator reconciliation

For claims already stranded by an older release, an absent receipt means the plugin cannot determine whether items were delivered. Do not bulk-reset `delivery_pending` or all claimed rows.

1. Stop the server, retain a coordinated backup, and record the mail ID, recipient UUID, generation, status and existing receipt files in an incident record. Inspect the recipient's inventory and currency/server logs to establish whether delivery occurred.
2. If delivery is proven, an administrator can clear only that matching claimed row's pending flag without changing its status or payload. If non-delivery is proven, restore only that matching row to `UNCLAIMED` or `RETURNED` as appropriate, clear its pending flag, and increment `claim_generation` in the same SQLite transaction. Do not perform either operation while the outcome is uncertain.
3. Record the operator, evidence, chosen outcome, exact before/after row and timestamp in the incident record. Archive any superseded receipt with that record before restarting. Check that subsequent receipt replay and another claim do not repeat delivery.

Use a copy of the backup to rehearse reconciliation before touching the stopped server's data. This procedure does not infer missing delivery evidence and is not an automatic repair for ambiguous claims.

## Required staging acceptance

Record the exact Paper/Java, Vault, EnthusiaCurrency, CombatLogX (and its required libraries), Nexo and listener-plugin versions. Use the real Nexo resource pack and configured icons; keep staging balances and inventory disposable.

- Send using bank-only, physical-only and combined currency balances. Verify the quote precedes withdrawal and the successful fee is charged once. Reject insufficient balance without taking cargo.
- Exercise a currency withdrawal listener that closes/replaces the menu. Confirm no outgoing package is inserted and that cargo and any successful fee are returned once. Repeat with a rejected payment and with combat or disconnect during payment.
- Claim while disconnecting, losing permission, entering combat or filling inventory. Hold a database write lock longer than the busy timeout, release it, restart, then confirm one claimable package and one final inventory delivery.
- Verify delivered and undelivered receipts separately. Replay a stale restoration receipt after a newer claim and confirm it does not reopen the package. Test malformed receipts and disk-full/read-only conditions on disposable data.
- Navigate rapidly through inbox and sent categories during slow storage. Confirm cursor placement, selected-category labels, receipt recovery, and other players' sends remain usable.
- Test Nexo enabled, disabled, unavailable and not-yet-loaded items. Check title glyphs, fallback icons, click controls, package placement markers, overflow and sounds on a real client.
- Stop cleanly during pending sends and claims, then restart. Rehearse coordinated backup/restore and explicitly assess abrupt-stop uncertainty. Inventory, third-party currency and SQLite do not share an atomic transaction.

Claimed package data remains retained for history and incident recovery; monitor database size and define a deliberate retention policy. Chat filtering remains outside this change as requested.
