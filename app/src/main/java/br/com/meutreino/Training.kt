package br.com.meutreino

import java.math.BigDecimal
import java.text.Normalizer
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val cardio: Boolean = false,
    val unit: String = "kg",
    val sets: String = "3",
    /** Meta de repetições por série. Atingir a meta em todas as séries libera o aumento de carga. */
    val target: String = "",
    val increment: String = "",
    /** Descanso em segundos depois de cada série. Zero usa o padrão do app. */
    val rest: Int = 0,
    /** Id do exercício na biblioteca (fotos e instruções). Vazio para exercícios criados à mão. */
    val ref: String = "",
    val video: String = "",
    val note: String = "",
    /** Carga planejada, usada como sugestão enquanto não há registro anterior. */
    val load: String = "",
    /** Variação de um exercício da biblioteca ("unilateral", "pegada neutra"). Tem histórico próprio. */
    val variant: String = ""
) {
    /** Identifica o mesmo exercício entre rotinas e treinos diferentes. */
    fun key() = ref.ifBlank { name.normalized() } + variant.normalized().let { if (it.isEmpty()) "" else "+$it" }

    fun plan(): String = when {
        cardio -> note.ifBlank { "Cardio" }
        else -> buildString {
            append(sets.ifBlank { "1" }).append(" × ").append(target.ifBlank { "livre" })
            if (rest > 0) append(" · ").append(clock(rest)).append(" de descanso")
        }
    }
}

/**
 * Uma série. Em força, [first] é a carga e [second] são as repetições. Em cardio, [first] são os minutos,
 * [second] a distância em km, [speed] a velocidade ou o nível do aparelho e [calories] as calorias gastas.
 */
data class SetEntry(
    val first: String = "",
    val second: String = "",
    val done: Boolean = false,
    val speed: String = "",
    val calories: String = ""
) {
    fun empty() = first.isBlank() && second.isBlank() && speed.isBlank() && calories.isBlank()

    fun valid(cardio: Boolean): Boolean {
        if (empty()) return true
        return if (cardio) {
            first.decimal()?.let { it.signum() > 0 } == true &&
                (second.isBlank() || second.decimal() != null) &&
                (speed.isBlank() || speed.decimal() != null) &&
                (calories.isBlank() || calories.decimal() != null)
        } else {
            first.decimal() != null && (second.toIntOrNull() ?: 0) > 0
        }
    }

    /** "30 min · 5 km · vel. 10 · 320 kcal", só com o que foi preenchido. */
    fun cardioSummary(): String = listOfNotNull(
        "$first min",
        second.takeIf { it.isNotBlank() }?.let { "$it km" },
        speed.takeIf { it.isNotBlank() }?.let { "vel. $it" },
        calories.takeIf { it.isNotBlank() }?.let { "$it kcal" }
    ).joinToString(" · ")
}

data class Entry(val exercise: Exercise, val sets: List<SetEntry>) {
    fun nextLoad(): String? {
        if (!readyToIncrease()) return null
        val increment = exercise.increment.decimal()?.takeIf { it.signum() > 0 } ?: return null
        val load = sets.first().first.decimal() ?: return null
        return (load + increment).plain()
    }

    fun readyToIncrease(): Boolean {
        val target = exercise.target.toIntOrNull()?.takeIf { it > 0 } ?: return false
        if (exercise.cardio || sets.isEmpty() || sets.size < (exercise.sets.toIntOrNull() ?: 1)) return false
        val load = sets.first().first.decimal() ?: return false
        return sets.all { set ->
            set.first.decimal()?.compareTo(load) == 0 && (set.second.toIntOrNull() ?: 0) >= target
        }
    }

    /** Maior carga usada em uma série válida. */
    fun bestLoad(): BigDecimal? = strengthSets().maxOfOrNull { it.first }

    /** Soma de carga × repetições. */
    fun volume(): BigDecimal = strengthSets().fold(BigDecimal.ZERO) { sum, (load, reps) -> sum + load * reps.toBigDecimal() }

    /** Melhor 1RM estimado pela fórmula de Epley: carga × (1 + repetições / 30). */
    fun oneRepMax(): Double? = strengthSets().maxOfOrNull { (load, reps) ->
        if (reps == 1) load.toDouble() else load.toDouble() * (1 + reps / 30.0)
    }

    private fun strengthSets(): List<Pair<BigDecimal, Int>> = if (exercise.cardio) emptyList() else sets.mapNotNull { set ->
        val load = set.first.decimal() ?: return@mapNotNull null
        val reps = set.second.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        load to reps
    }

    fun summary(): String = if (exercise.cardio) {
        sets.joinToString("  |  ") { it.cardioSummary() }
    } else {
        // 40 kg × 12 · 12 · 10 quando a carga se repete; senão série a série
        val loads = sets.map { it.first.decimal() }
        if (sets.size > 1 && loads.all { it != null && it.compareTo(loads.first()) == 0 }) {
            "${sets.first().first} ${exercise.unit} × " + sets.joinToString(" · ") { it.second }
        } else sets.joinToString(" · ") { "${it.first} ${exercise.unit} × ${it.second}" }
    }
}

/** O que sugerir para a próxima sessão de um exercício de força. */
data class Suggestion(val load: String, val reps: String, val increase: Boolean)

/**
 * Progressão dupla: enquanto a meta de repetições não é atingida em todas as séries planejadas com a
 * mesma carga, repete a carga. Quando é, soma o incremento. Sem registro anterior, usa a carga planejada.
 */
fun suggestion(exercise: Exercise, previous: Entry?): Suggestion? {
    if (exercise.cardio) return null
    if (previous == null || previous.sets.isEmpty()) {
        return exercise.load.takeIf { it.decimal() != null }?.let { Suggestion(it, exercise.target, false) }
    }
    val judged = previous.copy(exercise = exercise)
    judged.nextLoad()?.let { return Suggestion(it, exercise.target.ifBlank { previous.sets.first().second }, true) }
    val last = previous.sets.first()
    if (last.first.decimal() == null) return null
    return Suggestion(last.first, exercise.target.ifBlank { last.second }, false)
}

data class RoutineDraft(val id: Long = 0, val name: String = "", val exercises: List<Exercise> = emptyList())

data class WorkoutDraft(
    val id: Long = 0,
    val routineId: Long? = null,
    val name: String = "",
    val date: Long = System.currentTimeMillis(),
    val entries: List<Entry> = emptyList(),
    /** Duração em segundos de um registro já salvo. Em treinos novos é calculada a partir de [date]. */
    val duration: Long = 0,
    /** Avaliação do treino, de 1 a 5. Zero quando o treino não foi avaliado. */
    val rating: Int = 0,
    val note: String = ""
)

fun String.decimal() = trim().replace(',', '.').toBigDecimalOrNull()
    ?.takeIf { it.signum() >= 0 }

fun BigDecimal.plain(): String = (if (signum() == 0) BigDecimal.ZERO else stripTrailingZeros())
    .toPlainString().replace('.', ',')

/** Minúsculas, sem acentos e sem pontuação: "Tríceps  (corda)" vira "triceps corda". */
fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
    .replace(Regex("[\\u0300-\\u036f]"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

/** 90 vira "1:30". */
fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun clock(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    else clock(seconds.toInt())

/** 3700 segundos viram "1 h 01 min"; menos de um minuto aparece em segundos. */
fun durationLabel(seconds: Long): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${(seconds + 30) / 60} min"
    else -> "%d h %02d min".format(seconds / 3600, seconds % 3600 / 60)
}

private fun Exercise.json() = JSONObject()
    .put("id", id).put("name", name).put("cardio", cardio)
    .put("unit", unit).put("sets", sets).put("target", target).put("increment", increment)
    .put("rest", rest).put("ref", ref).put("video", video).put("note", note).put("load", load).put("variant", variant)

// Campos acrescentados depois da versão 1 são opcionais para ler registros antigos.
private fun JSONObject.exercise() = Exercise(
    getString("id"), getString("name"), getBoolean("cardio"),
    getString("unit"), getString("sets"), getString("target"), optString("increment"),
    optInt("rest"), optString("ref"), optString("video"), optString("note"), optString("load"), optString("variant")
)

fun List<Exercise>.encodeExercises() = JSONArray(map { it.json() }).toString()
fun String.decodeExercises() = JSONArray(this).let { array ->
    List(array.length()) { array.getJSONObject(it).exercise() }
}

private fun List<Entry>.entriesJson() = JSONArray(map { entry ->
    JSONObject().put("exercise", entry.exercise.json()).put("series", JSONArray(entry.sets.map {
        JSONObject().put("first", it.first).put("second", it.second).put("done", it.done).apply {
            if (it.speed.isNotBlank()) put("speed", it.speed)
            if (it.calories.isNotBlank()) put("kcal", it.calories)
        }
    }))
})

private fun JSONArray.entries() = List(length()) { index ->
    val entry = getJSONObject(index)
    val series = entry.getJSONArray("series")
    Entry(entry.getJSONObject("exercise").exercise(), List(series.length()) {
        val set = series.getJSONObject(it)
        SetEntry(set.getString("first"), set.getString("second"), set.optBoolean("done"),
            set.optString("speed"), set.optString("kcal"))
    })
}

fun List<Entry>.encodeEntries() = entriesJson().toString()
fun String.decodeEntries() = JSONArray(this).entries()

/** Guarda o treino em andamento para ele sobreviver ao fechamento do app. */
fun WorkoutDraft.encodeDraft() = JSONObject()
    .put("id", id).put("routineId", routineId ?: JSONObject.NULL).put("name", name)
    .put("date", date).put("duration", duration).put("rating", rating).put("note", note)
    .put("entries", entries.entriesJson()).toString()

fun String.decodeDraft() = JSONObject(this).let {
    WorkoutDraft(it.getLong("id"), if (it.isNull("routineId")) null else it.getLong("routineId"),
        it.getString("name"), it.getLong("date"), it.getJSONArray("entries").entries(), it.optLong("duration"),
        it.optInt("rating").coerceIn(0, 5), it.optString("note"))
}
