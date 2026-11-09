# Enthusia Express implementation

## Read-only museum preview

Preview controls load the selected artwork through the same submitter-or-manager scope as review details. Map identifiers select existing MapViews on the server thread; tile order is ascending map ID and is not claimed to be the artwork's spatial arrangement. Copy counts do not duplicate preview pages. Indexed submissions use their stored manifest. Legacy submissions without indexed tiles derive a temporary manifest from retained cargo using the existing payload-size and bounded container-scanning policies after authorization; no cargo is claimed, rewritten or migrated. A temporary self-only equipment display presents a filled map and clears the displayed offhand while leaving the authoritative inventory untouched; sendMap sends the selected tile. Exit and interruption restore the real inventory display. No world entities, blocks, maps, renderers, database rows, inventory items or external packet libraries are created for preview ownership. Automated interaction tests are bounded evidence; native Java/Bedrock rendering needs separately authorized client acceptance.

## Optional Post Office presentation

The original red/gold/cream assets are source-controlled under assets/nexo-postoffice and independent of Nexo's full server pack. GuiTheme creates viewer-bound adapters; the Post Office profile requires an accepted Java pack, six resolvable inventory glyphs and the transparent control item. Backend Floodgate identity failures refuse the custom profile. Missing optional assets retain vanilla controls. Native input/review dialogs retain normal widgets and use a glyph banner only when available.

Visible item art is referenced by the existing Enthusia Pack registry IDs. Public icon-sources.json stores pinned source paths and hashes, never private licensed pixels. Only original backgrounds/banner and transparent support chrome are generated here. GuiTheme applies the selected-tab glint to its cloned viewer item; it never changes a registry template. Inventory caption bands include the full client font height and lower home icons remain left of their labels. All baked captions use unscaled default Minecraft bitmap glyphs loaded from the official client. The generator rejects labels wider than their reserved rectangles; short shipping helper copy retains full instructions in existing tooltips.

PostOfficeLayout owns presentation-only slot maps. MuseumView protects all click/drag paths, maps every home-card slot to its existing command action, and revalidates the live actor and inventory next tick. Catalog entries occupy 18..44, independently of footer controls. Mailbox categories retain their anchors and reuse the active inventory; wide tabs route all three visual slots to their anchor. Shipping's owner, cargo marker, quote, payment and recovery lifecycle are unchanged. Scoped themes are released on inventory close. No schema or companion-plugin API signature is changed by this presentation layer.

Gradle's test inputs include the asset directory so texture/config edits invalidate the artifact-contract test. JUnit validates registry bindings, image dimensions and glyph definitions. Actual UI alignment, Nexo dispatch and player interactions require the separately authorized TEST client run; passing unit tests is not that acceptance.

## Layer Dependency Rules

The source root is `src/main/kotlin/io/enthusia/express`.

| Layer | Files | Allowed dependencies |
| --- | --- | --- |
| domain | `domain/**` | Domain, Kotlin/Java standard libraries |
| application | `application/**` | Domain, Kotlin/Java standard libraries |
| infrastructure | `infrastructure/**` | Application, domain, Paper, CombatLogX and JDBC |

Dependency direction: domain <- application <- infrastructure. `MailStore` is an application port implemented by the SQLite adapter. Bukkit services depend on that port. Domain mail records and enums contain no Bukkit or SQL types. Framework-dependent services remain adapters; this migration does not claim all business policies have been extracted from Bukkit.

## Forbidden Domain Annotations

```yaml
forbidden: []
```

SPEAR's default denylist applies: Spring, JPA, Jackson, Micronaut and Lombok. Framework imports are also excluded from domain/application; these types use only the language/JDK libraries and domain.

## Behavioral compatibility

The migration changes implementation language and package organization. It retains `mail.db` schema, serialized ItemStack bytes, mail status names, command syntax, permission nodes and configuration keys. The plugin descriptor points at the new infrastructure entry class. No recursion is introduced; container traversal remains iterative. Existing Java regression tests remain as independent clients of the Kotlin JVM API.

## Thread ownership

Inventory/player reads and writes and completion callbacks run on the primary server thread. A cache-miss name-to-UUID resolution uses Paper's profile source on a scheduler worker; its timed completion returns through MainThread and rechecks sender identity, permissions and combat before proceeding. SQLite work runs on one executor, with conditional SQL and transactions handling contention across independent connections. Disable drains completion callbacks before closing the repository. SQL and Minecraft inventory writes are still not one crash-atomic transaction.

Before a nonzero Vault withdrawal, a small payment intent is forced synchronously to disk. This deliberate ordering prevents a provider debit before reconciliation evidence exists; storage failure refuses the debit. Unknown provider outcomes retain this operator-only record and never trigger a blind refund. Measure force-write latency on staging. Delivery and restoration receipt processing remains on its dedicated worker.

## Verification

Use the original 38 regression tests, Kotlin migration/packaging tests, the upstream SPEAR Konsist template with project package substitution, and a compiled project call-graph cycle audit. SPEAR tests must not pass merely because domain/application layers are empty. API matrix compilations must use Kotlin source, not an empty Java source set.

## SPEAR adoption

Pinned upstream: BadgersMC/spear-plugin `2c91bae046649035f4abaa3c563f6676399e2eee`, Codex skills. Existing project purpose, users and goals come from the user's request and README. The four documents bootstrap SPEAR for this existing codebase. State lives in gitignored `.claude/spear-state.json`. Evidence is populated from verified source/API documentation before implementation. The Kotlin rewrite is one coordinated migration; worker briefings are each bounded to a package group and do not advance global state.

## Delivery reservation and CI

The additive delivery_pending column reserves package capacity between a conditional claim and server-thread inventory delivery. Acknowledgment clears it; compensation is conditional on it. Startup serializes migration and bounds retries for competing WAL initializers. GitHub Actions repeats representative API test runs and the eleven-target Kotlin compile gate, publishing baseline artifacts and reports with read-only repository permissions.

## Event delivery retry ownership

The infrastructure EventDeliveryService owns one private in-flight outcome per token. A matching recipient joins through a separate dependent future; caller cancellation or manual completion cannot settle or evict that owner. A recipient conflict remains an uncertain failure. The owner is installed before packaging or immediately completed callbacks can run, and is removed by entry identity only after persistence and any reconciliation settle. All ItemStack validation and packing remains synchronous on the caller's server thread.

Synchronous packing or repository-submission failures and asynchronous repository failures trigger an internal JDK-only findEventDelivery lookup through the same serialized repository queue as writes. An earlier accepted write therefore completes before the lookup can prove absence. A stored token resolves its original mail ID even after claim; an absent token preserves the original failure and its classification. Unavailable storage or a conflicting recipient yields a non-IllegalArgumentException failure, retaining the earlier failure as a suppressed diagnostic when lookup fails. An unknown result never authorizes a caller to release a second physical copy. The public EventDelivery JVM signature and SQLite schema are unchanged.

The event insert checks the configured package payload byte limit only for a new token, after the canonical token lookup and before either insert. This preserves a known delivery when a later configuration lowers the limit. The internal lookup port defaults to a future failed with IllegalStateException for alternate stores so absent implementation cannot masquerade as authoritative absence.

Validation at source e7bc6109e66a89c08f40c9e3aa6bbe48bb1322f1 includes genuine old-source behavioral failures followed by 30 passing focused cases and a clean 285-test build. The real SQLite fixture covers a committed token after a lost reply, repository reopen and changed packaging policy; concurrency cases cover FIFO resolution, overlapping recipients and caller cancellation/manual completion. This provider validation does not establish the separate Holidays consumer's runtime activation or actual client collection. The phase and artifact record is in docs/evidence/event-delivery-recovery-20261020.json.

## Reviewed delivery recovery

The delivery receipt worker owns all receipt files and serializes disk I/O outside the server thread. Inventory delivery happens first; the worker then forces receipt content to disk before clearing the SQLite reservation. It retries retained receipts every five seconds and on restart, and recognizes already acknowledged rows idempotently. Shutdown drains main-thread completions, closes the receipt worker and finally closes SQLite. Receipt replay never restores or redelivers items. An abrupt crash before durable recording remains an uncertain delivery requiring administrator reconciliation.

Connection cleanup preserves a successful transaction result after commit; reset/recovery errors are logged rather than triggering shipment compensation for an already stored row. The optional CombatLogX adapter invokes the public API directly, with absent-classpath and non-public-implementation regressions. Payment failures carry the actual provider so combined currency balances retain fractional units in messages.

## Paper 26 verification

The baseline build retains the Paper 1.21 API and Java 21 bytecode. Pinned 26.2 and 26.3-pre-2 verification builds select a Java 25 toolchain and distinct artifact classifiers. The baselineJar test property replaces production class directories on the test runtime classpath with the actual baseline shaded JAR; test compilation still uses the target API. This exercises binary compatibility without shipping an artifact built against a newer API to older servers. GitHub's Java 25 jobs install both required JDKs. Prerelease checks and mocked integration regressions do not replace live staging or final-release verification.

## Shipping compensation

`ShippingRecovery` is an infrastructure-only, main-thread-owned journal. Forced atomic phase changes distinguish definite unattempted compensation from ambiguous asset mutation. `ShippingService` retains live provider receipts, polls a bounded rotating pending batch and participates in EnthusiaCurrency's operation-owned movement lease. Restart recovery recreates only the persisted payment route. `PREPARED` and `APPLYING` are evidence for reconciliation, never automatic refund requests. Claim delivery now carries its callback context in a data class and separates eligibility, inventory mutation and rollback; exception cleanup preserves propagation through `finally`.

## Recipient presence privacy

The infrastructure RecipientPresence adapter uses Paper Player.canSee on the sending player and current recipient session. Online-only mail gates apply only to visible recipients; hidden recipients retain offline delivery semantics. Missing session handles retain the prior online rejection. Shipping rechecks this policy at open, confirmation, block-lookup completion and after payment callbacks. No staff ranks, permissions or provider internals are inferred; no new API dependency is bundled.

## Museum extension (REQ-051 through REQ-059)

JDK-only museum records and callback tokens live in domain/application. MuseumCatalog is owned by the serialized SQLite worker; additive metadata, manifests, revision guards, audit history and durable notices participate in the caller's existing transactions. List projections omit cargo BLOBs. MuseumMenus and DialogMenus are platform adapters; optional public Paper, ViaVersion and Floodgate/Cumulus APIs are discovered at runtime with fallback. Forms never own cargo: opening them first closes the inventory through its existing owner. Physical shipping, compensation and claim-generation receipts remain authoritative. Acknowledged collection and curator display are separate events.

Final Paper26.3.build.205-stable is a pinned Java25 verification profile. The distributable remains the Java21 baseline. The notification adapter supports literal configured messages in native/actionbar/chat mode without changing client-owned native frame headings. See museum-dialogs.md for commands, optional dependencies, migration and unresolved client gates.

