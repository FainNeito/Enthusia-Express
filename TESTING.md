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
- Enable each `mail.limits` option. A second unread letter or unresolved package to the same recipient should be blocked; a different recipient remains independent. Read the letter or finish claiming the package, then verify sending becomes available.
- Join with unread mail and verify category counts, then test an empty inbox and disabled notifications. Customize each success sound and confirm failed operations play no success cue.
- Back up an existing database before first upgrade. Verify retained books and packages survive the additive migration. Abrupt crashes can require manual reconciliation between inventory and the delivery reservation; this is not an exactly-once crash-atomic system.

Automated integration tests use real SQLite and mocked Paper/Vault services. Live behavior with your server's currency and combat plugins still needs the checks above.

- Retain delivery-receipts/ with database backups. After a simulated acknowledgment failure on a disposable server, restart and verify the reservation clears without giving a second package. Unknown deliveries without a durable receipt still need manual inventory reconciliation.
- Confirm insufficient currency messages show fractional combined balances, and tab completion performs no offline-file scan.
