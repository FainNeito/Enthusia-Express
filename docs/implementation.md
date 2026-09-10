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

## Verification

Use the original 38 regression tests, Kotlin migration/packaging tests, the upstream SPEAR Konsist template with project package substitution, and a compiled project call-graph cycle audit. SPEAR tests must not pass merely because domain/application layers are empty. API matrix compilations must use Kotlin source, not an empty Java source set.

## SPEAR adoption

Pinned upstream: BadgersMC/spear-plugin `2c91bae046649035f4abaa3c563f6676399e2eee`, Codex skills. Existing project purpose, users and goals come from the user's request and README. The four documents bootstrap SPEAR for this existing codebase. State lives in gitignored `.claude/spear-state.json`. Evidence is populated from verified source/API documentation before implementation. The Kotlin rewrite is one coordinated migration; worker briefings are each bounded to a package group and do not advance global state.

## Delivery reservation and CI

The additive delivery_pending column reserves package capacity between a conditional claim and server-thread inventory delivery. Acknowledgment clears it; compensation is conditional on it. Startup serializes migration and bounds retries for competing WAL initializers. GitHub Actions repeats representative API test runs and the eleven-target Kotlin compile gate, publishing baseline artifacts and reports with read-only repository permissions.

## Reviewed delivery recovery

The delivery receipt worker owns all receipt files and serializes disk I/O outside the server thread. Inventory delivery happens first; the worker then forces receipt content to disk before clearing the SQLite reservation. It retries retained receipts every five seconds and on restart, and recognizes already acknowledged rows idempotently. Shutdown drains main-thread completions, closes the receipt worker and finally closes SQLite. Receipt replay never restores or redelivers items. An abrupt crash before durable recording remains an uncertain delivery requiring administrator reconciliation.

Connection cleanup preserves a successful transaction result after commit; reset/recovery errors are logged rather than triggering shipment compensation for an already stored row. The optional CombatLogX adapter invokes the public API directly, with absent-classpath and non-public-implementation regressions. Payment failures carry the actual provider so combined currency balances retain fractional units in messages.
