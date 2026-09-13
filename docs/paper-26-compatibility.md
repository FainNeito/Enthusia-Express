# Paper 26 compatibility

Verified API artifacts (2026-09-13):

| Target | Pinned Paper API | Java | Status |
| --- | --- | --- | --- |
| Baseline | 1.21-R0.1-SNAPSHOT | 21 | Clean build; 149 tests passed |
| 26.2 | 26.2.build.123-stable | 25 | Clean build; 149 tests passed against baseline JAR |
| 26.3 prerelease | 26.3-pre-2.build.0-alpha | 25 | Clean build; 149 tests passed against baseline JAR |
| Final 26.3 | Not published in the checked Maven metadata | 25 expected | Not verified |

All eleven Paper 1.21 API compilation targets also passed. Production API usage did not need changes. Gradle 9.1.0, Kotlin 2.3.21, Shadow 9.2.2, Mockito 5.20.0 and test-only ASM 9.8 provide the Java 25 verification toolchain. The wrapper distribution is SHA-256 pinned. `api-version: '1.21'` remains the minimum supported API; the baseline plugin classes retain Java 21 bytecode.

The Java 25 jobs compile the source/tests against the target API, then replace the production class directories on the test runtime classpath with the baseline shaded JAR. This checks that the distributable works with the newer API in the regression fixtures. Verification outputs have distinct `-verify-<target>` names; install the baseline `EnthusiaExpress-1.2.1.jar` for staging.

The suite includes real SQLite concurrency, migration, claim recovery and native-driver loading, plus mocked currency, CombatLogX, Nexo, inventory, GUI and callback regressions. It also checks layer rules and compiled project call cycles. Java 25 emits a native-access warning while loading SQLite and an Unsafe deprecation warning from the test-only Konsist compiler dependency; these did not fail either Java 25 run. Bukkit legacy API deprecation warnings remain.

## Staging and release limits

No live Minecraft server or real client was used. Test the actual Java 25-compatible versions of EnthusiaCurrency, Vault, CombatLogX and its dependencies, and Nexo with your real configuration and resource pack. Follow [production safety acceptance](production-safety.md), including inventory serialization across upgrade, fee accounting, disconnects, restart recovery, combat blocking, custom GUI icons and title rendering. Keep coordinated backups before a server upgrade.

The 26.3 result applies only to the pinned prerelease. A final release can change API or server behavior and must be verified separately. This change does not grant production approval or establish Folia support.

## Reproduction and sources

Use the commands in [README](../README.md). Install JDK 21 and JDK 25; Gradle chooses the toolchain from `paperVersion`. The default build produces the baseline JAR. GitHub Actions keeps the three representative Java 21 jobs and adds both Java 25 targets, without making prerelease failures optional.

Local evidence is in `outputs/paper-26-compatibility` in the working session. Dependency downloads used an untracked loopback helper: Python verified upstream HTTPS, while Gradle used local HTTP to avoid a stalled local Java HTTP client. It forwarded unmodified official artifacts and was not added to the repository. The earlier untracked SQLite Maven override was retained. CI uses the ordinary official HTTPS repositories.

- [Paper Maven metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)
- [Paper project setup and version format](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper Java requirements](https://docs.papermc.io/paper/getting-started/)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
