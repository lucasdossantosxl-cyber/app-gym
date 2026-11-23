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
    val how: String,
    /** Id cujas fotos ilustram este exercício: o próprio, o de um exercício parecido ou vazio quando não há foto. */
    val photo: String = id
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

    /** Para cada exercício com foto, o id usado no nome dos arquivos em assets/ex/. */
    val photos: Map<String, String> = items.filter { it.photo.isNotEmpty() }.associate { it.id to it.photo }

    operator fun get(id: String): CatalogItem? = byId[id]

    /**
     * Como [match], mas separa as palavras que indicam uma variação ("unilateral", "alternado") quando o
     * exercício encontrado não as tem no nome: "Tríceps corda unilateral" vira tríceps na corda + "unilateral".
     */
    fun matchVariant(name: String): Pair<CatalogItem, String>? {
        val words = name.replace(Regex("\\(.*?\\)"), " ").normalized().split(' ').filter { it.isNotEmpty() }
        val extra = words.filter { it in VARIANT_WORDS }
        if (extra.isEmpty()) return match(name)?.let { it to "" }
        exact[name.normalized()]?.let { return it to "" }
        val stripped = words.filterNot { it in VARIANT_WORDS }.joinToString(" ")
        fun CatalogItem.has(word: String) = word in this.name.normalized() || aliases.any { word in it }
        exact[stripped]?.let { item -> return item to extra.filterNot { item.has(it) }.joinToString(" ") }
        // um exercício da biblioteca que já é a própria variação ("Rosca alternada") fica como está
        val full = match(name)
        if (full != null && extra.all { full.has(it) }) return full to ""
        return (match(stripped) ?: full)?.let { item -> item to extra.filterNot { item.has(it) }.joinToString(" ") }
    }

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
                    List(aliases.length()) { aliases.getString(it) }, item.getString("how"),
                    if (item.has("photo")) item.getString("photo") else item.getString("id"))
            })
        }

        private val VARIANT_WORDS = setOf("unilateral", "alternado", "alternada", "isometrico", "isometrica")

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

/** Onde a interface encontra as fotos da biblioteca. Preenchido quando o catálogo é carregado. */
object Photos {
    @Volatile var byRef: Map<String, String> = emptyMap()
}

/** Sugestões mostradas ao escolher a variação de um exercício. */
val VARIANTS = listOf("Unilateral", "Alternado", "Pegada neutra", "Pegada supinada", "Pegada pronada", "Pegada aberta",
    "Pegada fechada", "Sentado", "Em pé", "Inclinado", "Com pausa", "Com elástico")

/** Aplica (ou tira, com texto vazio) uma variação. O nome mostrado passa a ser "Exercício · variação". */
fun Exercise.withVariant(text: String, catalog: Catalog): Exercise {
    val base = catalog[ref]?.name ?: name.removeSuffix(" · $variant").ifBlank { name }
    val clean = text.trim().take(40).replaceFirstChar { it.lowercase() }
    return copy(variant = clean, name = if (clean.isEmpty()) base else "$base · $clean")
}
