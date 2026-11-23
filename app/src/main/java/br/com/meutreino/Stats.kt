package br.com.meutreino

import java.math.BigDecimal

/** Rotina com os exercícios já decodificados, para a interface não reprocessar JSON a cada desenho. */
data class RoutineItem(val routine: Routine, val exercises: List<Exercise>)

/** Treino salvo com as séries já decodificadas. */
data class Session(val workout: Workout, val entries: List<Entry>) {
    val volume: BigDecimal = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.volume() }
    val setCount: Int = entries.sumOf { it.sets.size }
}

/** Um treino na linha do tempo de um exercício. */
data class Point(
    val date: Long,
    val workoutId: Long,
    val best: Double,
    val oneRepMax: Double,
    val volume: Double,
    val summary: String
)

data class ExerciseStats(
    val key: String,
    val name: String,
    val ref: String,
    val cardio: Boolean,
    val unit: String,
    /** Do treino mais antigo para o mais recente. */
    val points: List<Point>
) {
    val bestLoad get() = points.maxOfOrNull { it.best } ?: 0.0
    val bestOneRepMax get() = points.maxOfOrNull { it.oneRepMax } ?: 0.0
    val bestVolume get() = points.maxOfOrNull { it.volume } ?: 0.0
}

enum class Metric(val label: String) {
    Load("Carga máxima"), OneRepMax("1RM estimado"), Volume("Volume");

    fun of(point: Point) = when (this) {
        Load -> point.best
        OneRepMax -> point.oneRepMax
        Volume -> point.volume
    }
}

/** Agrupa o histórico por exercício. O exercício usado mais recentemente vem primeiro. */
fun List<Session>.exerciseStats(): List<ExerciseStats> {
    class Group(val exercise: Exercise) {
        val points = mutableListOf<Point>()
    }
    val groups = LinkedHashMap<String, Group>()
    // as sessões chegam da mais recente para a mais antiga
    for (session in this) {
        for (entry in session.entries) {
            if (entry.sets.isEmpty() || entry.exercise.name.isBlank()) continue
            val key = (if (entry.exercise.cardio) "c:" else "f:${entry.exercise.unit.lowercase()}:") + entry.exercise.key()
            val group = groups.getOrPut(key) { Group(entry.exercise) }
            group.points += if (entry.exercise.cardio) {
                val minutes = entry.sets.sumOf { it.first.decimal()?.toDouble() ?: 0.0 }
                val distance = entry.sets.sumOf { it.second.decimal()?.toDouble() ?: 0.0 }
                Point(session.workout.date, session.workout.id, minutes, distance, distance, entry.summary())
            } else {
                Point(session.workout.date, session.workout.id, entry.bestLoad()?.toDouble() ?: 0.0,
                    entry.oneRepMax() ?: 0.0, entry.volume().toDouble(), entry.summary())
            }
        }
    }
    return groups.map { (key, group) ->
        ExerciseStats(key, group.exercise.name, group.exercise.ref, group.exercise.cardio, group.exercise.unit,
            group.points.reversed())
    }
}

/** Último registro do mesmo exercício antes do treino que está sendo preenchido. */
fun List<Session>.previous(exercise: Exercise, draft: WorkoutDraft): Entry? {
    val key = exercise.key()
    if (key.isEmpty()) return null
    return asSequence()
        .filter { it.workout.id != draft.id && (draft.id == 0L || it.workout.date < draft.date) }
        .flatMap { it.entries.asSequence() }
        .firstOrNull {
            it.exercise.cardio == exercise.cardio && it.exercise.key() == key &&
                (exercise.cardio || it.exercise.unit.equals(exercise.unit.trim(), true))
        }
}

/** 1234.5 vira "1.234,5"; valores inteiros saem sem casas decimais. */
fun Double.pretty(): String {
    val rounded = Math.round(this * 10) / 10.0
    val whole = rounded.toLong()
    val text = "%,d".format(java.util.Locale.ROOT, whole).replace(',', '.')
    val tenth = Math.round((rounded - whole) * 10).toInt()
    return if (tenth == 0) text else "$text,$tenth"
}

fun BigDecimal.pretty(): String = toDouble().pretty()
