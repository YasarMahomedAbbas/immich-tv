package io.github.yasarmahomedabbas.immichtv

import io.github.yasarmahomedabbas.immichtv.LayerRules.ONLY_APP_CONTAINER
import io.github.yasarmahomedabbas.immichtv.LayerRules.PLAIN_DOMAIN
import io.github.yasarmahomedabbas.immichtv.LayerRules.ROOT
import io.github.yasarmahomedabbas.immichtv.LayerRules.UI_DOMAIN_DATA
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerRulesTest {
    private val pkg = "io.github.yasarmahomedabbas.immichtv"

    private fun source(path: String, vararg lines: String) = SourceFile("$ROOT/$path", lines.joinToString("\n"))

    private fun assertViolations(expected: List<Violation>, vararg files: SourceFile) =
        assertEquals(expected, LayerRules.check(files.toList()))

    @Test
    fun `a clean ui, domain and data set passes`() = assertViolations(
        emptyList(),
        source("ui/home/HomeScreen.kt", "package $pkg.ui.home", "", "import androidx.compose.runtime.Composable", "import $pkg.domain.Album"),
        source("domain/Album.kt", "package $pkg.domain", "", "data class Album(val title: String)"),
        source("data/ImmichAlbums.kt", "package $pkg.data", "", "import okhttp3.OkHttpClient", "import $pkg.domain.Album"),
    )

    @Test
    fun `ui importing data fails`() = assertViolations(
        listOf(Violation("$ROOT/ui/home/HomeScreen.kt", 3, UI_DOMAIN_DATA, "$pkg.data.X")),
        source("ui/home/HomeScreen.kt", "package $pkg.ui.home", "", "import $pkg.data.X"),
    )

    @Test
    fun `domain importing data fails`() = assertViolations(
        listOf(Violation("$ROOT/domain/Album.kt", 3, UI_DOMAIN_DATA, "$pkg.data.X")),
        source("domain/Album.kt", "package $pkg.domain", "", "import $pkg.data.X"),
    )

    private fun assertDomainImportFails(name: String) = assertViolations(
        listOf(Violation("$ROOT/domain/Album.kt", 3, PLAIN_DOMAIN, name)),
        source("domain/Album.kt", "package $pkg.domain", "", "import $name"),
    )

    @Test
    fun `domain importing android fails`() = assertDomainImportFails("android.os.Bundle")

    @Test
    fun `domain importing androidx fails`() = assertDomainImportFails("androidx.annotation.Keep")

    @Test
    fun `domain importing okhttp fails`() = assertDomainImportFails("okhttp3.OkHttpClient")

    @Test
    fun `domain importing kotlinx serialization fails`() = assertDomainImportFails("kotlinx.serialization.Serializable")

    @Test
    fun `an inline qualified data name in ui fails`() = assertViolations(
        listOf(Violation("$ROOT/ui/home/HomeScreen.kt", 3, UI_DOMAIN_DATA, "$pkg.data.X")),
        source("ui/home/HomeScreen.kt", "package $pkg.ui.home", "", "val x = $pkg.data.X()"),
    )

    @Test
    fun `MainActivity naming data fails`() = assertViolations(
        listOf(Violation("$ROOT/MainActivity.kt", 3, ONLY_APP_CONTAINER, "$pkg.data.X")),
        source("MainActivity.kt", "package $pkg", "", "import $pkg.data.X"),
    )

    @Test
    fun `AppContainer naming data passes`() = assertViolations(
        emptyList(),
        source("AppContainer.kt", "package $pkg", "", "import $pkg.data.X", "val x = $pkg.data.Y()"),
    )

    @Test
    fun `data files naming each other pass`() = assertViolations(
        emptyList(),
        source("data/A.kt", "package $pkg.data", "", "import $pkg.data.http.B"),
        source("data/http/B.kt", "package $pkg.data.http", "", "val a = $pkg.data.A()"),
    )

    @Test
    fun `data inside a comment or string literal in domain passes`() = assertViolations(
        emptyList(),
        source(
            "domain/Album.kt",
            "package $pkg.domain",
            "// see $pkg.data.X and okhttp3.OkHttpClient",
            "/* android.os.Bundle",
            "   $pkg.data.Y */",
            "val a = \"$pkg.data.X \\\" okhttp3.Call\"",
            "val b = \"\"\"",
            "kotlinx.serialization.Json",
            "\"\"\"",
            "val c = '\"'",
        ),
    )

    @Test
    fun `a line after a blanked comment keeps its number`() = assertViolations(
        listOf(Violation("$ROOT/domain/Album.kt", 4, PLAIN_DOMAIN, "okhttp3.Call")),
        source("domain/Album.kt", "package $pkg.domain", "/* one", "   two */", "val c: okhttp3.Call? = null"),
    )

    @Test
    fun `domain importing coroutines flow passes`() = assertViolations(
        emptyList(),
        source("domain/Album.kt", "package $pkg.domain", "", "import kotlinx.coroutines.flow.Flow"),
    )

    @Test
    fun `a violation message names the file, line and rule`() {
        val violation = LayerRules.check(listOf(source("domain/Album.kt", "package $pkg.domain", "import okhttp3.Call")))
        assertEquals(listOf("$ROOT/domain/Album.kt:2: $PLAIN_DOMAIN (okhttp3.Call)"), violation.map { it.toString() })
    }
}
