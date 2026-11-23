package br.com.meutreino

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapTest {
    private val zone = ZoneOffset.UTC
    private val supino = Exercise(name = "Supino reto com barra", ref = "supino-reto", sets = "2", target = "10")
    private val esteira = Exercise(name = "Corrida na esteira", ref = "esteira-corrida", cardio = true, unit = "km")
    private var nextId = 1L

    private fun at(date: String) = LocalDate.parse(date).atTime(18, 0).toInstant(zone).toEpochMilli()

    private fun session(date: String, name: String, minutes: Long, rating: Int, vararg entries: Entry) =
        Session(Workout(nextId++, null, name, at(date), entries.toList().encodeEntries(), minutes * 60, rating), entries.toList())

    private fun lift(load: String) = Entry(supino, listOf(SetEntry(load, "10"), SetEntry(load, "10")))
    private fun run(minutes: String, km: String, kcal: String) =
        Entry(esteira, listOf(SetEntry(minutes, km, speed = "10", calories = kcal)))

    // como no app: do treino mais recente para o mais antigo
    private val sessions = listOf(
        session("2026-11-20", "Treino A", 50, 5, lift("60"), run("20", "3,5", "250")),
        session("2026-11-19", "Treino A", 40, 4, lift("55")),
        session("2026-11-18", "Cardio", 30, 0, run("30", "5", "380,5")),
        session("2026-11-03", "Treino A", 45, 3, lift("52,5")),
        session("2026-10-28", "Treino A", 60, 0, lift("50")),
        session("2026-10-10", "Treino A", 55, 0, lift("47,5"))
    )

    private fun november() = sessions.recap(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 10, 1), zone)

    @Test
    fun countsWorkoutsDaysTimeAndLoad() {
        val recap = november()
        assertEquals(4, recap.workouts)
        assertEquals(4, recap.days)
        assertEquals(165L * 60, recap.seconds)
        // 2 séries × 10 repetições × (60 + 55 + 52,5)
        assertEquals(3350.0, recap.volume, 0.001)
        assertEquals(8, recap.sets)
        assertEquals(3, recap.streak)
        assertEquals(2, recap.previousWorkouts)
        assertEquals(1950.0, recap.previousVolume, 0.001)
    }

    @Test
    fun sumsCardioWithSpeedAndCalories() {
        val recap = november()
        assertEquals(2, recap.cardioSessions)
        assertEquals(50.0, recap.cardioMinutes, 0.001)
        assertEquals(8.5, recap.cardioDistance, 0.001)
        assertEquals(630.5, recap.calories, 0.001)
        assertEquals("20 min · 3,5 km · vel. 10 · 250 kcal", sessions.first().entries.last().summary())
    }

    @Test
    fun findsFavouritesRecordsAndRatings() {
        val recap = november()
        assertEquals(listOf("Supino reto com barra" to 3, "Corrida na esteira" to 2), recap.topExercises.map { it.name to it.count })
        assertEquals("Treino A", recap.topRoutine)
        assertEquals(3, recap.topRoutineCount)
        // outubro foi até 50 kg; novembro chegou a 60 kg
        val record = recap.records.single()
        assertEquals(50.0, record.before, 0.001)
        assertEquals(60.0, record.now, 0.001)
        // a esteira nunca tinha aparecido antes de novembro
        assertEquals(1, recap.newExercises)
        assertEquals(3, recap.rated)
        assertEquals(4.0, recap.averageRating, 0.001)
        assertEquals(50L * 60, recap.longest!!.workout.duration)
        assertTrue(recap.weekday in setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
    }

    @Test
    fun emptyPeriodsAndTheWholeYear() {
        val december = sessions.recap(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1), LocalDate.of(2026, 11, 1), zone)
        assertTrue(december.empty)
        assertEquals(4, december.previousWorkouts)

        val year = sessions.recap(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), LocalDate.of(2025, 1, 1), zone)
        assertEquals(6, year.workouts)
        assertTrue(year.records.isEmpty())
        assertEquals(0, year.newExercises)
        assertTrue(year.shareText("2026").contains("6 treinos em 6 dias"))
    }
}
