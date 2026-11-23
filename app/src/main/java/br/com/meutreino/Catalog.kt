package br.com.meutreino

import org.json.JSONArray

/** Exercício da biblioteca embutida no app (assets/exercises.json, fotos em assets/ex/). */
data class CatalogItem(
    val id: String,
    val name: String,
    val group: String,
    val equipment: String,
    val cardio: Boolean,
    val increment: String,
    val aliases: List<String>,
    val how: String
) {
    fun toExercise() = Exercise(name = name, cardio = cardio, unit = if (cardio) "km" else "kg",
        sets = if (cardio) "1" else "3", target = if (cardio) "" else "12", increment = increment, ref = id)
}

class Catalog(val items: List<CatalogItem>) {
    private val byId = items.associateBy { it.id }
    private val keys: List<Pair<Set<String>, CatalogItem>> = items.flatMap { item ->
        (item.aliases + item.name).map { it.tokens() to item }
    }
    private val exact: Map<String, CatalogItem> = buildMap {
        // o nome completo tem prioridade sobre apelidos iguais de outro exercício
        items.forEach { item -> item.aliases.forEach { putIfAbsent(it.normalized(), item) } }
        items.forEach { put(it.name.normalized(), it) }
    }
    val groups: List<String> = items.map { it.group }.distinct()

    operator fun get(id: String): CatalogItem? = byId[id]

    /** Encontra o exercício da biblioteca que corresponde a um nome escrito livremente. */
    fun match(name: String): CatalogItem? {
        val plain = name.replace(Regex("\\(.*?\\)"), " ").normalized()
        exact[name.normalized()]?.let { return it }
        exact[plain]?.let { return it }
        val wanted = plain.tokens()
        if (wanted.isEmpty()) return null
        var best: CatalogItem? = null
        var bestScore = 0.0
        for ((tokens, item) in keys) {
            val common = tokens.count { it in wanted }
            if (common == 0) continue
            val score = common.toDouble() / (tokens.size + wanted.size - common)
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best.takeIf { bestScore >= 0.6 }
    }

    fun search(query: String, group: String?): List<CatalogItem> {
        val words = query.normalized().split(' ').filter { it.isNotEmpty() }
        return items.filter { item ->
            (group == null || item.group == group) && words.all { word ->
                item.name.normalized().contains(word) || item.aliases.any { it.contains(word) } ||
                    item.equipment.normalized().contains(word) || item.group.normalized().contains(word)
            }
        }
    }

    companion object {
        fun parse(json: String): Catalog = JSONArray(json).let { array ->
            Catalog(List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val aliases = item.getJSONArray("aliases")
                CatalogItem(item.getString("id"), item.getString("name"), item.getString("group"),
                    item.getString("equipment"), item.getBoolean("cardio"), item.getString("increment"),
                    List(aliases.length()) { aliases.getString(it) }, item.getString("how"))
            })
        }

        private val FILLER = setOf("com", "na", "no", "nas", "nos", "de", "da", "do", "em", "a", "o", "e", "para", "ao")

        /** Palavras relevantes do nome, sem preposições e sem plural: "com halteres" vira {halter}. */
        private fun String.tokens(): Set<String> = normalized().split(' ')
            .filter { it.isNotEmpty() && it !in FILLER }
            .map { word ->
                when {
                    word.length > 4 && word.endsWith("es") -> word.dropLast(2)
                    word.length > 3 && word.endsWith("s") -> word.dropLast(1)
                    else -> word
                }
            }.toSet()
    }
}
