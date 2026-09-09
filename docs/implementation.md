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

Bukkit reads/writes and completion callbacks run on the primary server thread. SQLite work runs on one executor, with conditional SQL and transactions handling contention across independent connections. Disable drains completion callbacks before closing the repository. SQL and Minecraft inventory writes are still not one crash-atomic transaction.

## Verification

Use the original 38 regression tests, Kotlin migration/packaging tests, the upstream SPEAR Konsist template with project package substitution, and a compiled project call-graph cycle audit. SPEAR tests must not pass merely because domain/application layers are empty. API matrix compilations must use Kotlin source, not an empty Java source set.

## SPEAR adoption

Pinned upstream: BadgersMC/spear-plugin `2c91bae046649035f4abaa3c563f6676399e2eee`, Codex skills. Existing project purpose, users and goals come from the user's request and README. The four documents bootstrap SPEAR for this existing codebase. State lives in gitignored `.claude/spear-state.json`. Evidence is populated from verified source/API documentation before implementation. The Kotlin rewrite is one coordinated migration; worker briefings are each bounded to a package group and do not advance global state.

## Delivery reservation and CI

The additive delivery_pending column reserves package capacity between a conditional claim and server-thread inventory delivery. Acknowledgment clears it; compensation is conditional on it. Startup serializes migration and bounds retries for competing WAL initializers. GitHub Actions repeats representative API test runs and the eleven-target Kotlin compile gate, publishing baseline artifacts and reports with read-only repository permissions.
