package br.com.meutreino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLogicTest {
    private val bench = Exercise(name = "Supino", sets = "2", target = "12", increment = "1,25")
    private val set = SetEntry("7,5", "12")

    @Test
    fun progressionNeedsAllPlannedSetsAtTheSameLoadAndTargetReps() {
        val exercise = bench.copy(increment = "")
        assertTrue(set.valid(cardio = false))
        assertTrue(Entry(exercise, listOf(set, SetEntry("7.50", "13"))).readyToIncrease())
        assertFalse(Entry(exercise, listOf(set)).readyToIncrease())
        assertFalse(Entry(exercise, listOf(set, SetEntry("7,5", "11"))).readyToIncrease())
        assertFalse(Entry(exercise, listOf(set, SetEntry("8", "12"))).readyToIncrease())
        assertFalse(Entry(exercise.copy(target = ""), listOf(set, set)).readyToIncrease())
        assertFalse(Entry(exercise, emptyList()).readyToIncrease())
    }

    @Test
    fun progressionUsesTheConfiguredLoadIncrement() {
        val entry = Entry(bench, listOf(set, set))
        assertEquals("8,75", entry.nextLoad())
        assertNull(entry.copy(sets = listOf(set)).nextLoad())
        assertNull(entry.copy(exercise = bench.copy(increment = "0")).nextLoad())
        assertNull(entry.copy(exercise = bench.copy(increment = "")).nextLoad())
    }

    @Test
    fun suggestionRaisesLoadOnlyAfterTheTargetIsMet() {
        assertEquals(Suggestion("8,75", "12", true), suggestion(bench, Entry(bench, listOf(set, set))))
        // uma série abaixo da meta: repete a carga
        assertEquals(Suggestion("7,5", "12", false), suggestion(bench, Entry(bench, listOf(set, SetEntry("7,5", "10")))))
        // sem histórico usa a carga planejada na rotina, quando existe
        assertEquals(Suggestion("20", "12", false), suggestion(bench.copy(load = "20"), null))
        assertNull(suggestion(bench, null))
        assertNull(suggestion(Exercise(name = "Esteira", cardio = true), Entry(bench, listOf(set))))
    }

    @Test
    fun suggestionJudgesTheLastWorkoutByTheCurrentPlan() {
        // o registro antigo foi feito com meta 10; a rotina agora pede 12
        val old = Entry(bench.copy(target = "10"), listOf(SetEntry("7,5", "10"), SetEntry("7,5", "10")))
        assertEquals(Suggestion("7,5", "12", false), suggestion(bench, old))
    }

    @Test
    fun recordsFromVersionOneStillDecode() {
        val exercises = """[{"id":"a","name":"Supino","cardio":false,"unit":"kg","sets":"3","target":"12","increment":"1"}]"""
        assertEquals(listOf(Exercise("a", "Supino", false, "kg", "3", "12", "1")), exercises.decodeExercises())
        val entries = """[{"exercise":{"id":"a","name":"Supino","cardio":false,"unit":"kg","sets":"3","target":"12"},""" +
            """"series":[{"first":"40","second":"12"}]}]"""
        assertEquals(listOf(Entry(Exercise("a", "Supino", false, "kg", "3", "12"), listOf(SetEntry("40", "12")))),
            entries.decodeEntries())
    }

    @Test
    fun newFieldsSurviveEncoding() {
        val exercise = Exercise(name = "Supino reto com barra", rest = 90, ref = "supino-reto", video = "https://exemplo.com/v",
            note = "8–12 repetições", load = "40")
        assertEquals(listOf(exercise), listOf(exercise).encodeExercises().decodeExercises())
        val entries = listOf(Entry(exercise, listOf(SetEntry("40", "12", done = true), SetEntry("40", "10"))))
        assertEquals(entries, entries.encodeEntries().decodeEntries())
        val draft = WorkoutDraft(routineId = 7, name = "Treino A", date = 1234, entries = entries)
        assertEquals(draft, draft.encodeDraft().decodeDraft())
        assertEquals(draft.copy(routineId = null), draft.copy(routineId = null).encodeDraft().decodeDraft())
    }

    @Test
    fun entryStatistics() {
        val entry = Entry(bench, listOf(SetEntry("40", "10"), SetEntry("42,5", "8"), SetEntry("", "")))
        assertEquals("42.5", entry.bestLoad()!!.toPlainString())
        assertEquals(0, entry.volume().compareTo(740.toBigDecimal()))
        assertEquals(42.5 * (1 + 8 / 30.0), entry.oneRepMax()!!, 0.001)
        assertEquals("40 kg × 10 · 10", Entry(bench, listOf(SetEntry("40", "10"), SetEntry("40", "10"))).summary())
        assertEquals("40 kg × 10 · 42,5 kg × 8", Entry(bench, listOf(SetEntry("40", "10"), SetEntry("42,5", "8"))).summary())
    }

    @Test
    fun historyIsGroupedByExerciseAcrossRoutines() {
        fun session(id: Long, date: Long, routine: Long?, vararg entries: Entry) =
            Session(Workout(id, routine, "Treino", date, "[]"), entries.toList())
        val library = Exercise(name = "Supino reto com barra", ref = "supino-reto")
        val renamed = Exercise(name = "Supino", ref = "supino-reto")
        val custom = Exercise(name = "Flexão Nórdica")
        val sessions = listOf(
            session(3, 300, null, Entry(renamed, listOf(SetEntry("45", "8")))),
            session(2, 200, 1, Entry(custom, listOf(SetEntry("0", "6")))),
            session(1, 100, 1, Entry(library, listOf(SetEntry("40", "10"), SetEntry("40", "9"))))
        )
        val stats = sessions.exerciseStats()
        assertEquals(listOf("Supino", "Flexão Nórdica"), stats.map { it.name })
        assertEquals(listOf(40.0, 45.0), stats[0].points.map { it.best })
        assertEquals(45.0, stats[0].bestLoad, 0.0)

        // o último registro vem de qualquer rotina, mas nunca do próprio treino nem de treinos posteriores
        assertEquals("45", sessions.previous(library, WorkoutDraft())!!.sets.first().first)
        assertEquals("40", sessions.previous(library, WorkoutDraft(id = 3, date = 300))!!.sets.first().first)
        assertNull(sessions.previous(library, WorkoutDraft(id = 1, date = 100)))
        assertNull(sessions.previous(library.copy(unit = "lb"), WorkoutDraft()))
        assertEquals("0", sessions.previous(Exercise(name = "flexao nordica"), WorkoutDraft())!!.sets.first().first)
    }

    @Test
    fun formatting() {
        assertEquals("1:30", clock(90))
        assertEquals("1:01:05", clock(3665L))
        assertEquals("triceps corda", "Tríceps  (corda)".normalized())
        assertEquals("1.234,5", 1234.5.pretty())
        assertEquals("40", 40.0.pretty())
        assertEquals("8,75", "8.750".decimal()!!.plain())
    }
}
