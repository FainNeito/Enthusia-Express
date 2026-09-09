# Enthusia Express technology stack

- Output: Paper plugin for players and server administrators; test release 1.2.0.
- Kotlin/JVM 2.2.21, Java 21, Gradle wrapper 8.14.3.
- Paper API 1.21 default; 11 API compile configurations through 1.21.11.
- Shadow 8.3.6, SQLite JDBC 3.50.3.0; Kotlin standard library and JDBC/native resources included in shaded JAR.
- JUnit Jupiter 5.11.4, Mockito 5.15.2, Konsist 0.17.3 for architecture.
- Test-only published CombatLogX API 11.7-SNAPSHOT and SirBlobman core 2.9-SNAPSHOT.
- Java regression tests are intentionally retained; production code is Kotlin.
- SPEAR state/requirements helpers run locally from the pinned upstream checkout. State is excluded from distribution and Git.
- No automatic GitHub Actions or live Minecraft server result is implied by local tests.

Sources: https://kotlinlang.org/docs/gradle-configure-project.html (Kotlin 2.2.21/Gradle compatibility); https://github.com/LemonAppDev/konsist/blob/main/README.md (0.17.3 dependency/API); merged build.gradle.kts, plugin.yml and VERIFICATION.md (existing dependencies and behavior).
