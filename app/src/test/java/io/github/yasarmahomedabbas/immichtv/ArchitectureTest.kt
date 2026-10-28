package io.github.yasarmahomedabbas.immichtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs [LayerRules] over the app's real main sources, so CI fails on a layer violation. */
class ArchitectureTest {
    @Test
    fun `the main sources keep the layer rules`() {
        // Gradle runs unit tests with the module directory as the working directory.
        val root = File("src/main/java")
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { SourceFile(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
            .toList()
        assertTrue("no Kotlin sources found under ${root.absolutePath}", files.isNotEmpty())

        val violations = LayerRules.check(files)
        assertEquals(violations.joinToString("\n"), emptyList<Violation>(), violations)
    }
}
