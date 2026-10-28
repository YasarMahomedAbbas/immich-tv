package io.github.yasarmahomedabbas.immichtv

/** A main source file: [path] relative to `src/main/java` with `/` separators. */
data class SourceFile(val path: String, val text: String)

/** One broken layer rule: [reference] is the qualified name that broke [rule] at [path]:[line]. */
data class Violation(val path: String, val line: Int, val rule: String, val reference: String) {
    override fun toString() = "$path:$line: $rule ($reference)"
}

/**
 * The layer rules from CLAUDE.md § Layers, checked by name: `ui/` → `domain/` ← `data/`, and
 * only `AppContainer` names a `data/` class. Comments and string literals are blanked first so
 * prose and text never count; imports and inline qualified names both do.
 */
object LayerRules {
    const val ROOT = "io/github/yasarmahomedabbas/immichtv"

    const val UI_DOMAIN_DATA = "ui/ and domain/ never import data/"
    const val ONLY_APP_CONTAINER = "only AppContainer names a data/ class"
    const val PLAIN_DOMAIN = "domain/ is plain Kotlin: no Android, OkHttp or kotlinx.serialization"

    private const val DATA_PACKAGE = "io.github.yasarmahomedabbas.immichtv.data"
    private const val APP_CONTAINER = "$ROOT/AppContainer.kt"
    private val qualifiedName = Regex(
        """(?<![\w.])(io\.github\.yasarmahomedabbas\.immichtv\.data|androidx?|okhttp3|kotlinx\.serialization)\.[\w.]*""",
    )

    fun check(files: List<SourceFile>): List<Violation> = files.flatMap(::check)

    private fun check(file: SourceFile): List<Violation> {
        val layer = layerOf(file.path)
        if (layer == "data") return emptyList()
        return blankCommentsAndStrings(file.text).lines().flatMapIndexed { index, line ->
            qualifiedName.findAll(line).mapNotNull { match ->
                val rule = when {
                    match.groupValues[1] == DATA_PACKAGE -> when (layer) {
                        "ui", "domain" -> UI_DOMAIN_DATA
                        else -> if (file.path == APP_CONTAINER) null else ONLY_APP_CONTAINER
                    }
                    layer == "domain" -> PLAIN_DOMAIN
                    else -> null
                }
                rule?.let { Violation(file.path, index + 1, it, match.value.trimEnd('.')) }
            }.toList()
        }
    }

    private fun layerOf(path: String): String {
        if (!path.startsWith("$ROOT/")) return "root"
        val rest = path.removePrefix("$ROOT/")
        val segment = rest.substringBefore('/', missingDelimiterValue = "")
        return if (segment in setOf("ui", "domain", "data")) segment else "root"
    }

    /** Replaces comments, string and char literals with spaces, keeping every newline. */
    internal fun blankCommentsAndStrings(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        fun blankUntil(end: Int) {
            for (j in i until end) out.append(if (text[j] == '\n') '\n' else ' ')
            i = end
        }
        while (i < text.length) {
            val end = when {
                text.startsWith("//", i) -> text.indexOf('\n', i).let { if (it < 0) text.length else it }
                text.startsWith("/*", i) -> closeOf(text, "*/", i + 2)
                text.startsWith("\"\"\"", i) -> closeOf(text, "\"\"\"", i + 3)
                text[i] == '"' -> escapedCloseOf(text, '"', i + 1)
                text[i] == '\'' -> escapedCloseOf(text, '\'', i + 1)
                else -> -1
            }
            if (end < 0) {
                out.append(text[i])
                i++
            } else {
                blankUntil(end)
            }
        }
        return out.toString()
    }

    private fun closeOf(text: String, close: String, from: Int): Int =
        text.indexOf(close, from).let { if (it < 0) text.length else it + close.length }

    private fun escapedCloseOf(text: String, close: Char, from: Int): Int {
        var j = from
        while (j < text.length && text[j] != '\n') {
            when (text[j]) {
                '\\' -> j += 2
                close -> return j + 1
                else -> j++
            }
        }
        return minOf(j, text.length)
    }
}
