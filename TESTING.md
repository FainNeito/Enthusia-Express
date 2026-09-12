# Testing Enthusia Express 1.2.0

This is the Kotlin rewrite. Use Java 21 and an ordinary Paper 1.21.x test server.

1. Stop the server and back up player data and `plugins/EnthusiaExpress` together.
2. Replace the previous Enthusia Express JAR with `EnthusiaExpress-1.2.0.jar` in `plugins`. Keep only one version installed. The Kotlin runtime and SQLite driver are bundled.
3. Install CombatLogX and its required dependencies, or explicitly set `mail.require-combatlogx: false` for a test without combat protection. The default blocks mail when CombatLogX is missing.
4. Start the server. Confirm the plugin enables without errors and `/mail` opens the inbox.
5. With two known players, log the recipient out. Send a signed book with `/mail letter <player>`, then log in as the recipient and open it in the letters tab. Reopen it and confirm its unread flag clears.
6. As an operator, send `/mail announce <player>` and `/mail announce all`. Confirm a non-operator without the announcement permission is denied, and each recipient has independent unread state.
7. Send and claim a filled shulker box or bundle. Check the gold fee, cancel/refund behavior, full inventory handling, and that rapid repeat clicks cannot claim twice.
8. Tag a player using CombatLogX and confirm commands and pending mailbox actions are blocked. Repeat after combat ends.
9. On a disposable database, shorten the return/purge settings, restart, leave a package unclaimed, and check that it returns once and later purges. Do not shorten these settings on a valued mailbox database.
10. Restart with pending deliveries and confirm persistence and refunds. Keep the console log if anything fails.

Automated checks use real SQLite databases and mocked Paper interactions. They do not replace testing on your server with its other plugins. The plugin does not support Folia. Abrupt crashes cannot make Minecraft inventory files and SQLite commit atomically.

## Currency and new mail behavior

- Install Vault and EnthusiaCurrency. With `payments.provider: auto`, send from an account with sufficient bank balance and no physical raw gold; verify one fee deduction. Repeat with a combined bank/item balance, then with insufficient total funds; failed payment must retain the package.
- Test `physical` mode and automatic physical fallback with EnthusiaCurrency absent. With the required currency mode and a missing/disabled provider, confirm shipping is blocked without removing cargo.
- On a disposable test database, exercise persistence failure and outstanding-limit rejection. Verify cargo returns and the original fee reaches the currency bank once. Review server logs if the provider refuses a refund; never simulate database faults on production.
- Click the gray package-slot marker, cancel, close immediately after clicking, and try number keys, shift-click, double-click and drag. Verify the marker never escapes and cargo is neither lost nor duplicated.
- Enable each `mail.limits` option. A second unread letter or unresolved package to the same recipient should be blocked; a different recipient remains independent. Read the letter, or deliver the package and complete its acknowledgment, then verify sending becomes available. While `delivery_pending = 1`, the package remains outstanding; a failed acknowledgment releases the limit only when its durable retry completes. Failed inventory delivery restores the outstanding package rather than releasing its limit.
- Join with unread mail and verify category counts, then test an empty inbox and disabled notifications. Customize each success sound and confirm failed operations play no success cue.
- Back up an existing database before first upgrade. Verify retained books and packages survive the additive migration. Abrupt crashes can require manual reconciliation between inventory and the delivery reservation; this is not an exactly-once crash-atomic system.

Automated integration tests use real SQLite and mocked Paper/Vault services. Live behavior with your server's currency and combat plugins still needs the checks above.

- Retain delivery-receipts/ with database backups. After a simulated acknowledgment failure on a disposable server, restart and verify the reservation clears without giving a second package. Unknown deliveries without a durable receipt still need manual inventory reconciliation.
- Confirm insufficient currency messages show fractional combined balances. Completion uses a startup snapshot of known names, refreshed when players join; it performs no offline-file scan during completion.

## Gameplay feedback checks

- With the recipient online, use `/mail letter <player>` and confirm it explains that a letter is unnecessary. Announcements should still reach online players.
- Move the mouse away from the center, then navigate inbox categories and pages. The same inventory window should stay open and the mouse should remain in place. The title is `Mailbox`; category and page are shown inside the menu.
- Open `/mail send <player>` for a long recipient name. The title is `Send package`, with the recipient shown on the paper icon.
- Place a filled container and click `View postage`. Confirm the cost appears in chat and on the `Send package` button without deducting money. Click again to send. Replace the cargo after quoting: the next click must show a fresh quote before it can send. Repeat using physical Raw Gold and EnthusiaCurrency bank funds.
- Tab-complete a known offline player's name with `/mail send`, `/mail letter`, and `/mail announce`. Join with a new account and verify its name becomes available too.
- Join with one package only, multiple letters only, and mixed unread mail. Each notice should show only a nonempty category, correct singular/plural wording, and its matching inbox command.
- Existing configurations use defaults for the new `letter-target-online`, `letter-invalid-recipient`, `package-quote`, and `join-mail-packages/letters/announcements` messages. The old aggregate `join-mail` message is retired. In a customized `package-sent` message, replace the hardcoded `Raw Gold` label with `{currency}` to describe the selected payment route.

Chat-filter integration is deferred at the user's request. GUI cursor behavior and text fit still need confirmation on the actual client; automated tests check inventory identity and message contents.

## Sent history and category selection

- Use `/mail sent [packages|letters|announcements]`, or click `View sent mail` in the mailbox. Only your sent mail should appear, with original recipient, send time in UTC, and current status. Announcements have one row per recipient. History includes retained claimed, returned and expired entries.
- Hover over a retained package for its container preview and packed-item count. Clicking it must never collect or duplicate the package. Open a sent letter and confirm the recipient's copy stays unread.
- Send enough mail for two pages and navigate both directions. Switch categories while in Sent; it should remain in Sent. Click `Open inbox` to return to received mail.
- The selected category uses a green icon, a leading arrow, and `Selected` in its tooltip. The separate Inbox/Sent indicator shows the page number. Check that these stay current without reopening the inventory or recentering the cursor.
- Remove `enthusiaexpress.sent` permission and confirm history cannot open. This permission defaults to true; sending and claiming retain their existing permissions.
- Upgrade a backup of an existing database. The additive `original_recipient_name` column preserves destinations before future returns. Normal existing records are backfilled. Previously returned records whose destination was overwritten show `Unknown (legacy return)` rather than a guessed recipient. Expired content cannot be reopened, but retained metadata stays visible.
# Recipient blocking and Nexo

- Block another player, restart, and inspect `/mail blocked`. Try a letter, package and announcement from that player, including a broadcast; verify no new mail or postage charge. Unblock and repeat. Verify existing mail and returned packages remain claimable.
- Deny `enthusiaexpress.block` and verify block management is unavailable. Check name completion for block/unblock.
- With Nexo absent or disabled, verify every normal menu control works. Follow NEXO.md with real pack assets; check category selection, stable cursor position, title alignment and all buttons.
- Use a custom shipping placeholder, close/reopen, and attempt click/drag/shift-click: no marker may enter player inventory or become a shipment. Unknown Nexo IDs must retain usable vanilla controls.
