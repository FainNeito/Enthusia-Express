# Enthusia Express implementation

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

## Reviewed delivery recovery

The delivery receipt worker owns all receipt files and serializes disk I/O outside the server thread. Inventory delivery happens first; the worker then forces receipt content to disk before clearing the SQLite reservation. It retries retained receipts every five seconds and on restart, and recognizes already acknowledged rows idempotently. Shutdown drains main-thread completions, closes the receipt worker and finally closes SQLite. Receipt replay never restores or redelivers items. An abrupt crash before durable recording remains an uncertain delivery requiring administrator reconciliation.

Connection cleanup preserves a successful transaction result after commit; reset/recovery errors are logged rather than triggering shipment compensation for an already stored row. The optional CombatLogX adapter invokes the public API directly, with absent-classpath and non-public-implementation regressions. Payment failures carry the actual provider so combined currency balances retain fractional units in messages.

## Paper 26 verification

The baseline build retains the Paper 1.21 API and Java 21 bytecode. Pinned 26.2 and 26.3-pre-2 verification builds select a Java 25 toolchain and distinct artifact classifiers. The baselineJar test property replaces production class directories on the test runtime classpath with the actual baseline shaded JAR; test compilation still uses the target API. This exercises binary compatibility without shipping an artifact built against a newer API to older servers. GitHub's Java 25 jobs install both required JDKs. Prerelease checks and mocked integration regressions do not replace live staging or final-release verification.

## Shipping compensation

`ShippingRecovery` is an infrastructure-only, main-thread-owned journal. Forced atomic phase changes distinguish definite unattempted compensation from ambiguous asset mutation. `ShippingService` retains live provider receipts, polls a bounded rotating pending batch and participates in EnthusiaCurrency's operation-owned movement lease. Restart recovery recreates only the persisted payment route. `PREPARED` and `APPLYING` are evidence for reconciliation, never automatic refund requests. Claim delivery now carries its callback context in a data class and separates eligibility, inventory mutation and rollback; exception cleanup preserves propagation through `finally`.
