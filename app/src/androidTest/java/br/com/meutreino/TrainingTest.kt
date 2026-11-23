package br.com.meutreino

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingTest {
    @Test
    fun workoutsSurviveReopenAndEditsStayInTheirRoutine() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "training-test-${UUID.randomUUID()}.db"
        val exercise = Exercise(name = "Supino", sets = "2", target = "12")
        val upperEntries = listOf(Entry(exercise, listOf(SetEntry("7,5", "12"), SetEntry("7.5", "12"))))
        val lowerEntries = listOf(Entry(Exercise(name = "Agachamento"), listOf(SetEntry("30", "10"))))
        val upper = Workout(routineId = 1, name = "Upper A", date = 1, entries = upperEntries.encodeEntries())
        val lower = Workout(routineId = 2, name = "Lower A", date = 2, entries = lowerEntries.encodeEntries())

        fun openDatabase() = Room.databaseBuilder(context, TrainingDatabase::class.java, dbName).build()

        try {
            val initial = openDatabase()
            try {
                initial.trainingDao().save(upper)
                initial.trainingDao().save(lower)
            } finally {
                initial.close()
            }

            val reopened = openDatabase()
            try {
                val dao = reopened.trainingDao()
                val saved = dao.workouts().first()
                assertEquals(listOf("Lower A", "Upper A"), saved.map { it.name })
                assertEquals(lowerEntries, saved[0].entries.decodeEntries())
                assertEquals(upperEntries, saved[1].entries.decodeEntries())
                assertTrue(saved[1].entries.decodeEntries().single().readyToIncrease())

                val edited = saved[1].copy(name = "Upper A revisado")
                dao.save(edited)
                val after = dao.workouts().first()
                assertEquals(listOf(saved[0], edited), after)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun progressionNeedsAllPlannedSetsAtTheSameLoadAndTargetReps() {
        val exercise = Exercise(name = "Supino", sets = "2", target = "12")
        val set = SetEntry("7,5", "12")
        assertTrue(set.valid(cardio = false))
        assertTrue(Entry(exercise, listOf(set, SetEntry("7.50", "13"))).readyToIncrease())
        assertFalse(Entry(exercise, listOf(set)).readyToIncrease())
        assertFalse(Entry(exercise, listOf(set, SetEntry("7,5", "11"))).readyToIncrease())
        assertFalse(Entry(exercise, listOf(set, SetEntry("8", "12"))).readyToIncrease())
        assertFalse(Entry(exercise.copy(target = ""), listOf(set, set)).readyToIncrease())
    }
}
