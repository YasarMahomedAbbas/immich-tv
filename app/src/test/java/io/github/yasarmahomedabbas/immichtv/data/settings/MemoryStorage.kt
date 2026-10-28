package io.github.yasarmahomedabbas.immichtv.data.settings

/** [StringStorage] in a map, for tests. */
internal class MemoryStorage : StringStorage {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(values: Map<String, String?>) {
        for ((key, value) in values) if (value == null) this.values.remove(key) else this.values[key] = value
    }
}
