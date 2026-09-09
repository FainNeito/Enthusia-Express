package io.enthusia.express.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * SPEAR layer-rule enforcement.
 *
 * Emitted by `/spear:init` on JVM projects (REQ-065). Mirrors the
 * three-layer hexagonal discipline SPEAR enforces: domain depends on
 * nothing, application depends only on domain, infrastructure is
 * unconstrained.
 *
 * If this project uses a non-standard top-level package, replace
 * `io.enthusia.express` throughout this file with the actual package.
 * `/spear:init` substitutes the detected package automatically; when
 * copying this template by hand, do the substitution manually.
 */
class LayerRulesTest {

    @Test
    fun `all layers exist and inner layers use only allowed imports`() {
        val files = Konsist.scopeFromProduction().files
        for (layer in listOf("domain", "application", "infrastructure")) {
            assertTrue(files.withPackage("io.enthusia.express.$layer..").isNotEmpty(), "$layer must contain source")
        }
        for (layer in listOf("domain", "application")) {
            files.withPackage("io.enthusia.express.$layer..").forEach { file ->
                file.imports.forEach { imported ->
                    assertTrue(imported.name.startsWith("java.") || imported.name.startsWith("kotlin.") ||
                        imported.name.startsWith("io.enthusia.express.domain."), "Forbidden inner-layer import: ${imported.name}")
                }
            }
        }
    }

    @Test
    fun `spear layer dependencies are correct`() {
        Konsist
            .scopeFromProduction()
            .assertArchitecture {
                val domain = Layer("Domain", "io.enthusia.express.domain..")
                val application = Layer("Application", "io.enthusia.express.application..")
                val infrastructure = Layer("Infrastructure", "io.enthusia.express.infrastructure..")

                domain.dependsOnNothing()
                application.dependsOn(domain)
                infrastructure.dependsOn(domain, application)
            }
    }

    @Test
    fun `domain has no framework annotations or imports`() {
        val forbiddenPrefixes = listOf(
            "org.springframework",
            "jakarta.persistence",
            "javax.persistence",
            "com.fasterxml.jackson",
            "io.micronaut",
            "lombok",
        )

        Konsist
            .scopeFromProduction()
            .files
            .withPackage("..domain..")
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenPrefixes.any { prefix -> import.name.startsWith(prefix) }
                }
            }
    }
}



