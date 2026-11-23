package br.com.meutreino

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Exercício mais feito em um período. */
data class RecapExercise(val name: String, val ref: String, val count: Int)

/** Recorde de carga batido em um período: de [before] para [now]. */
data class RecapRecord(val name: String, val ref: String, val unit: String, val before: Double, val now: Double)

/** Retrospectiva de um mês ou de um ano, no estilo das retrospectivas de apps de música. */
data class Recap(
    val workouts: Int = 0,
    val days: Int = 0,
    val seconds: Long = 0,
    val volume: Double = 0.0,
    val sets: Int = 0,
    val cardioSessions: Int = 0,
    val cardioMinutes: Double = 0.0,
    val cardioDistance: Double = 0.0,
    val calories: Double = 0.0,
    val topExercises: List<RecapExercise> = emptyList(),
    val exerciseCount: Int = 0,
    val topRoutine: String = "",
    val topRoutineCount: Int = 0,
    val weekday: DayOfWeek? = null,
    val weekdayCount: Int = 0,
    val records: List<RecapRecord> = emptyList(),
    val newExercises: Int = 0,
    val rated: Int = 0,
    val averageRating: Double = 0.0,
    /** Maior número de dias seguidos com treino. */
    val streak: Int = 0,
    val longest: Session? = null,
    val previousWorkouts: Int = 0,
    val previousVolume: Double = 0.0
) {
    val empty get() = workouts == 0
}

/**
 * Resume os treinos entre [from] (inclusive) e [to] (exclusive). O período anterior, usado na comparação,
 * começa em [previousFrom] e termina em [from].
 */
fun List<Session>.recap(from: LocalDate, to: LocalDate, previousFrom: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Recap {
    fun Session.day(): LocalDate = Instant.ofEpochMilli(workout.date).atZone(zone).toLocalDate()
    val dated = map { it to it.day() }
    val period = dated.filter { (_, day) -> !day.isBefore(from) && day.isBefore(to) }
    if (period.isEmpty()) return Recap(previousWorkouts = dated.count { (_, day) -> !day.isBefore(previousFrom) && day.isBefore(from) })
    val current = period.map { it.first }
    val previous = dated.filter { (_, day) -> !day.isBefore(previousFrom) && day.isBefore(from) }.map { it.first }
    val earlier = dated.filter { (_, day) -> day.isBefore(from) }.map { it.first }

    val days = period.map { it.second }.toSortedSet()
    var streak = 0
    var run = 0
    var last: LocalDate? = null
    for (day in days) {
        run = if (last != null && last.plusDays(1) == day) run + 1 else 1
        streak = maxOf(streak, run)
        last = day
    }

    // exercícios: quantos treinos tiveram cada um
    class Use(val exercise: Exercise) {
        var count = 0
        var best = 0.0
    }
    fun identity(exercise: Exercise) = (if (exercise.cardio) "c:" else "f:${exercise.unit.lowercase()}:") + exercise.key()
    val uses = LinkedHashMap<String, Use>()
    for (session in current) {
        for (entry in session.entries) {
            if (entry.sets.isEmpty() || entry.exercise.name.isBlank()) continue
            val use = uses.getOrPut(identity(entry.exercise)) { Use(entry.exercise) }
            use.count++
            use.best = maxOf(use.best, entry.bestLoad()?.toDouble() ?: 0.0)
        }
    }
    val before = HashMap<String, Double>()
    for (session in earlier) {
        for (entry in session.entries) {
            if (entry.sets.isEmpty()) continue
            before.merge(identity(entry.exercise), entry.bestLoad()?.toDouble() ?: 0.0, ::maxOf)
        }
    }
    val records = uses.mapNotNull { (key, use) ->
        val old = before[key] ?: return@mapNotNull null
        if (use.exercise.cardio || old <= 0 || use.best <= old) null
        else RecapRecord(use.exercise.name, use.exercise.ref, use.exercise.unit, old, use.best)
    }.sortedByDescending { it.now / it.before }

    val routine = current.groupingBy { it.workout.name }.eachCount().maxByOrNull { it.value }
    val weekday = period.groupingBy { it.second.dayOfWeek }.eachCount().maxByOrNull { it.value }
    val rated = current.filter { it.workout.rating > 0 }

    return Recap(
        workouts = current.size,
        days = days.size,
        seconds = current.sumOf { it.workout.duration },
        volume = current.sumOf { it.volume.toDouble() },
        sets = current.sumOf { it.setCount },
        cardioSessions = current.count { it.cardioMinutes > 0 },
        cardioMinutes = current.sumOf { it.cardioMinutes },
        cardioDistance = current.sumOf { it.cardioDistance },
        calories = current.sumOf { it.calories },
        topExercises = uses.values.sortedByDescending { it.count }.take(5)
            .map { RecapExercise(it.exercise.name, it.exercise.ref, it.count) },
        exerciseCount = uses.size,
        topRoutine = routine?.key.orEmpty(),
        topRoutineCount = routine?.value ?: 0,
        weekday = weekday?.key,
        weekdayCount = weekday?.value ?: 0,
        records = records.take(5),
        newExercises = if (earlier.isEmpty()) 0 else uses.keys.count { it !in before },
        rated = rated.size,
        averageRating = if (rated.isEmpty()) 0.0 else rated.sumOf { it.workout.rating }.toDouble() / rated.size,
        streak = streak,
        longest = current.maxByOrNull { it.workout.duration }?.takeIf { it.workout.duration > 0 },
        previousWorkouts = previous.size,
        previousVolume = previous.sumOf { it.volume.toDouble() }
    )
}

/** Texto curto da retrospectiva, para compartilhar. */
fun Recap.shareText(title: String): String = buildString {
    append("Meu Treino · ").append(title).append('\n')
    append(if (workouts == 1) "1 treino" else "$workouts treinos").append(" em ")
        .append(if (days == 1) "1 dia" else "$days dias").append('\n')
    if (seconds > 0) append("Tempo treinando: ").append(durationLabel(seconds)).append('\n')
    if (volume > 0) append("Carga levantada: ").append(volume.pretty()).append(" kg\n")
    if (cardioMinutes > 0) {
        append("Cardio: ").append(cardioMinutes.pretty()).append(" min")
        if (cardioDistance > 0) append(", ").append(cardioDistance.pretty()).append(" km")
        if (calories > 0) append(", ").append(calories.pretty()).append(" kcal")
        append('\n')
    }
    topExercises.firstOrNull()?.let { append("Exercício mais feito: ").append(it.name).append('\n') }
    if (records.isNotEmpty()) append("Recordes de carga: ").append(records.size).append('\n')
}.trim()
