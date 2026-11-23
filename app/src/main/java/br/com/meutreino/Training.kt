package br.com.meutreino

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val cardio: Boolean = false,
    val unit: String = "kg",
    val sets: String = "3",
    val target: String = "",
    val increment: String = ""
)

data class SetEntry(val first: String = "", val second: String = "") {
    fun empty() = first.isBlank() && second.isBlank()

    fun valid(cardio: Boolean): Boolean {
        if (empty()) return true
        return if (cardio) {
            first.decimal()?.let { it.signum() > 0 } == true &&
                (second.isBlank() || second.decimal() != null)
        } else {
            first.decimal() != null && (second.toIntOrNull() ?: 0) > 0
        }
    }
}

data class Entry(val exercise: Exercise, val sets: List<SetEntry>) {
    fun nextLoad(): String? {
        if (!readyToIncrease()) return null
        val increment = exercise.increment.decimal()?.takeIf { it.signum() > 0 } ?: return null
        val load = sets.first().first.decimal() ?: return null
        return (load + increment).stripTrailingZeros().toPlainString().replace('.', ',')
    }

    fun readyToIncrease(): Boolean {
        val target = exercise.target.toIntOrNull()?.takeIf { it > 0 } ?: return false
        if (exercise.cardio || sets.size < (exercise.sets.toIntOrNull() ?: 1)) return false
        val load = sets.firstOrNull()?.first?.decimal() ?: return false
        return sets.all { set ->
            set.first.decimal()?.compareTo(load) == 0 && (set.second.toIntOrNull() ?: 0) >= target
        }
    }
}

fun String.decimal() = trim().replace(',', '.').toBigDecimalOrNull()
    ?.takeIf { it.signum() >= 0 }

private fun Exercise.json() = JSONObject()
    .put("id", id).put("name", name).put("cardio", cardio)
    .put("unit", unit).put("sets", sets).put("target", target).put("increment", increment)

private fun JSONObject.exercise() = Exercise(
    getString("id"), getString("name"), getBoolean("cardio"),
    getString("unit"), getString("sets"), getString("target"), optString("increment")
)

fun List<Exercise>.encodeExercises() = JSONArray(map { it.json() }).toString()
fun String.decodeExercises() = JSONArray(this).let { array ->
    List(array.length()) { array.getJSONObject(it).exercise() }
}

fun List<Entry>.encodeEntries() = JSONArray(map { entry ->
    JSONObject().put("exercise", entry.exercise.json()).put("series", JSONArray(entry.sets.map {
        JSONObject().put("first", it.first).put("second", it.second)
    }))
}).toString()

fun String.decodeEntries() = JSONArray(this).let { array ->
    List(array.length()) { index ->
        val entry = array.getJSONObject(index)
        val series = entry.getJSONArray("series")
        Entry(entry.getJSONObject("exercise").exercise(), List(series.length()) {
            val set = series.getJSONObject(it)
            SetEntry(set.getString("first"), set.getString("second"))
        })
    }
}
