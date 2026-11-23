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
        .addMigrations(TrainingDatabase.MIGRATION_1_2).allowMainThreadQueries().build()

    @Test
    fun dataFromVersionOneSurvivesTheUpgrade() = runBlocking {
        createVersionOne("upgrade.db")
        val database = open("upgrade.db")
        try {
            val dao = database.trainingDao()
            val workout = dao.workouts().first().single()
            assertEquals("Upper A", workout.name)
            assertEquals(0L, workout.duration)
            assertEquals(entries, workout.entries.decodeEntries())
            assertEquals("Upper A", dao.routines().first().single().name)

            dao.save(workout.copy(duration = 3600))
            dao.save(Workout(routineId = null, name = "Avulso", date = 2000, entries = entries.encodeEntries(), duration = 1800))
            assertEquals(listOf(1800L, 3600L), dao.workouts().first().map { it.duration })
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
            source.trainingDao().add(Workout(routineId = routineId, name = "Upper A", date = 1000, entries = entries.encodeEntries(), duration = 900))
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
            assertEquals(entries, workouts.first().entries.decodeEntries())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test(expected = Exception::class)
    fun backupFromAnotherAppIsRejected() {
        Backup.decode("""{"app":"outro","routines":[],"workouts":[]}""")
    }
}
