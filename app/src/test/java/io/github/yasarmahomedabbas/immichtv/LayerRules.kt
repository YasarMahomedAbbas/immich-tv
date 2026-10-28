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

    /**
     * Replaces comments, string and char literals with spaces, keeping every newline. Block
     * comments nest, and the code inside a `${...}` string template is kept, since it can name
     * a class like any other code.
     */
    internal fun blankCommentsAndStrings(text: String): String = Blanker(text).apply { code(inTemplate = false) }.out.toString()

    private class Blanker(val text: String) {
        val out = StringBuilder(text.length)
        var i = 0

        private fun keep() {
            out.append(text[i])
            i++
        }

        private fun blank(count: Int = 1) = repeat(count) {
            if (i < text.length) {
                out.append(if (text[i] == '\n') '\n' else ' ')
                i++
            }
        }

        /** Copies code to the end, or, [inTemplate], up to the `}` that closes the template. */
        fun code(inTemplate: Boolean) {
            var depth = 0
            while (i < text.length) {
                when {
                    text.startsWith("//", i) -> while (i < text.length && text[i] != '\n') blank()
                    text.startsWith("/*", i) -> blockComment()
                    text.startsWith("\"\"\"", i) -> string(raw = true)
                    text[i] == '"' -> string(raw = false)
                    text[i] == '\'' -> charLiteral()
                    inTemplate && text[i] == '}' && depth == 0 -> return
                    else -> {
                        if (text[i] == '{') depth++ else if (text[i] == '}') depth--
                        keep()
                    }
                }
            }
        }

        private fun blockComment() {
            var depth = 0
            do {
                when {
                    text.startsWith("/*", i) -> depth++.also { blank(2) }
                    text.startsWith("*/", i) -> depth--.also { blank(2) }
                    else -> blank()
                }
            } while (i < text.length && depth > 0)
        }

        private fun string(raw: Boolean) {
            blank(if (raw) 3 else 1)
            while (i < text.length) {
                when {
                    raw && text.startsWith("\"\"\"", i) -> return blank(3)
                    !raw && text[i] == '"' -> return blank()
                    !raw && text[i] == '\n' -> return // unterminated: resume at the next line
                    !raw && text[i] == '\\' -> blank(2)
                    text.startsWith("\${", i) -> {
                        blank(2)
                        code(inTemplate = true)
                        blank() // the closing brace
                    }
                    else -> blank()
                }
            }
        }

        private fun charLiteral() {
            blank()
            while (i < text.length && text[i] != '\n') {
                when (text[i]) {
                    '\\' -> blank(2)
                    '\'' -> return blank()
                    else -> blank()
                }
            }
        }
    }
}
