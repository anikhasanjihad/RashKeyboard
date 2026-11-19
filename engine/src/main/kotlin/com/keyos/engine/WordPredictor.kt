package com.keyos.engine

/**
 * Prefix completion over a built-in starter list plus words the user actually types (learned locally,
 * never sent anywhere). Pure Kotlin so it is unit-testable.
 */
class WordPredictor(builtin: List<String> = CommonWords.list) {

    private val rank = HashMap<String, Int>().apply {
        builtin.forEachIndexed { i, w -> putIfAbsent(w.lowercase(), i) }
    }
    private val user = HashMap<String, Int>()

    var dirty = false
        private set

    fun learn(word: String) {
        val w = word.lowercase()
        if (w.length < 2 || w.length > MAX_WORD || !w.all { it.isLetter() || it == '\'' }) return
        user[w] = (user[w] ?: 0) + 1
        dirty = true
        if (user.size > MAX_USER) prune()
    }

    /** Up to [max] completions of [prefix] (excluding the prefix itself), best first, in the prefix's casing. */
    fun suggest(prefix: String, max: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val candidates = HashSet<String>()
        for (w in rank.keys) if (w.length > p.length && w.startsWith(p)) candidates.add(w)
        for ((w, c) in user) if (c >= 2 && w.length > p.length && w.startsWith(p)) candidates.add(w)
        return candidates
            .sortedWith(compareByDescending<String> { score(it) }.thenBy { it.length }.thenBy { it })
            .take(max)
            .map { applyCase(prefix, it) }
    }

    fun exportUser(): String = user.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun importUser(data: String) {
        user.clear()
        for (line in data.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size == 2) parts[1].toIntOrNull()?.let { if (parts[0].isNotEmpty()) user[parts[0]] = it }
        }
        dirty = false
    }

    fun markSaved() { dirty = false }

    private fun score(w: String): Int {
        val r = rank[w]
        return (user[w] ?: 0) * 10_000 + if (r != null) 5_000 - minOf(r, 4_999) else 0
    }

    private fun applyCase(prefix: String, word: String): String = when {
        prefix.length > 1 && prefix.all { it.isUpperCase() } -> word.uppercase()
        prefix[0].isUpperCase() -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    private fun prune() {
        val keep = user.entries.sortedByDescending { it.value }.take(MAX_USER * 4 / 5).map { it.key }.toSet()
        user.keys.retainAll(keep)
    }

    private companion object {
        const val MAX_WORD = 32
        const val MAX_USER = 5000
    }
}
