package br.com.meutreino

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entries = listOf(Entry(Exercise(name = "Supino", sets = "2", target = "12"),
        listOf(SetEntry("7,5", "12"), SetEntry("7.5", "12"))))

    /** Banco como a versão 1.1 do app criava, antes de existir a coluna de duração. */
    private fun createVersionOne(name: String) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `Routine` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `exercises` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Workout` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`routineId` INTEGER, `name` TEXT NOT NULL, `date` INTEGER NOT NULL, `entries` TEXT NOT NULL)")
            db.execSQL("INSERT INTO Routine (name, exercises) VALUES (?, ?)",
                arrayOf("Upper A", entries.map { it.exercise }.encodeExercises()))
            db.execSQL("INSERT INTO Workout (routineId, name, date, entries) VALUES (1, ?, 1000, ?)",
                arrayOf("Upper A", entries.encodeEntries()))
            db.version = 1
        }
    }

    private fun open(name: String) = Room.databaseBuilder(context, TrainingDatabase::class.java, name)
        .addMigrations(*TrainingDatabase.MIGRATIONS).allowMainThreadQueries().build()

    @Test
    fun dataFromVersionOneSurvivesTheUpgrade() = runBlocking {
        createVersionOne("upgrade.db")
        val database = open("upgrade.db")
        try {
            val dao = database.trainingDao()
            val workout = dao.workouts().first().single()
            assertEquals("Upper A", workout.name)
            assertEquals(0L, workout.duration)
            assertEquals(0, workout.rating)
            assertEquals("", workout.note)
            assertEquals(entries, workout.entries.decodeEntries())
            assertEquals("Upper A", dao.routines().first().single().name)

            dao.save(workout.copy(duration = 3600))
            dao.save(Workout(routineId = null, name = "Avulso", date = 2000, entries = entries.encodeEntries(), duration = 1800))
            assertEquals(listOf(1800L, 3600L), dao.workouts().first().map { it.duration })

            // um treino pode ser concluído sem nenhuma série, só com a avaliação
            dao.save(Workout(routineId = null, name = "Só presença", date = 3000, entries = "[]", duration = 60,
                rating = 4, note = "Fui, mas não anotei"))
            val empty = dao.workouts().first().first()
            assertEquals(4, empty.rating)
            assertEquals("Fui, mas não anotei", empty.note)
            assertEquals(emptyList<Entry>(), empty.entries.decodeEntries())
        } finally {
            database.close()
        }
    }

    @Test
    fun backupRoundTripMergesWithoutDuplicating() = runBlocking {
        val source = open("origem.db")
        val target = open("destino.db")
        try {
            val routineId = source.trainingDao().add(Routine(name = "Upper A", exercises = entries.map { it.exercise }.encodeExercises()))
            source.trainingDao().add(Workout(routineId = routineId, name = "Upper A", date = 1000, entries = entries.encodeEntries(),
                duration = 900, rating = 5, note = "Bati a meta"))
            source.trainingDao().add(Workout(routineId = null, name = "Avulso", date = 2000, entries = entries.encodeEntries()))
            val json = Backup.encode(source.trainingDao().allRoutines(), source.trainingDao().allWorkouts())
            assertEquals(true, Backup.looksLikeBackup(json))

            // o destino já tem outra rotina, então os ids não coincidem com os do backup
            val dao = target.trainingDao()
            dao.add(Routine(name = "Lower A", exercises = "[]"))
            assertEquals(1 to 2, Backup.restore(dao, Backup.decode(json)))
            assertEquals(0 to 0, Backup.restore(dao, Backup.decode(json)))

            val restored = dao.allRoutines().single { it.name == "Upper A" }
            val workouts = dao.allWorkouts()
            assertEquals(listOf(restored.id, null), workouts.map { it.routineId })
            assertEquals(listOf(900L, 0L), workouts.map { it.duration })
            assertEquals(listOf(5, 0), workouts.map { it.rating })
            assertEquals(listOf("Bati a meta", ""), workouts.map { it.note })
            assertEquals(entries, workouts.first().entries.decodeEntries())
        } finally {
            source.close()
            target.close()
        }
    }

    /** Backups feitos pela versão 2.0 não têm avaliação nem os campos novos de cardio. */
    @Test
    fun backupFromTheOlderVersionStillRestores() {
        val old = """{"app":"meutreino","version":1,"routines":[],"workouts":[{"routineId":null,"name":"Cardio","date":5,
            "duration":600,"entries":[{"exercise":{"id":"a","name":"Esteira","cardio":true,"unit":"km","sets":"1","target":""},
            "series":[{"first":"20","second":"3","done":true}]}]}]}"""
        val workout = Backup.decode(old).workouts.single()
        assertEquals(0, workout.rating)
        assertEquals(SetEntry("20", "3", true), workout.entries.decodeEntries().single().sets.single())
    }

    @Test(expected = Exception::class)
    fun backupFromAnotherAppIsRejected() {
        Backup.decode("""{"app":"outro","routines":[],"workouts":[]}""")
    }
}
