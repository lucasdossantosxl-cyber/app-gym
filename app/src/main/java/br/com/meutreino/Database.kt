package br.com.meutreino

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val exercises: String
)

@Entity
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val name: String,
    val date: Long,
    val entries: String,
    /** Duração em segundos. Zero em registros anteriores à versão 2. */
    @ColumnInfo(defaultValue = "0") val duration: Long = 0,
    /** Avaliação de 1 a 5 dada ao concluir o treino. Zero quando não houve avaliação. */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    @ColumnInfo(defaultValue = "") val note: String = ""
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM Routine ORDER BY name COLLATE NOCASE, id")
    fun routines(): Flow<List<Routine>>

    @Query("SELECT * FROM Workout ORDER BY date DESC, id DESC")
    fun workouts(): Flow<List<Workout>>

    @Query("SELECT * FROM Routine ORDER BY id")
    suspend fun allRoutines(): List<Routine>

    @Query("SELECT * FROM Workout ORDER BY date, id")
    suspend fun allWorkouts(): List<Workout>

    @Upsert suspend fun save(routine: Routine)
    @Upsert suspend fun save(workout: Workout)
    @Insert suspend fun add(routine: Routine): Long
    @Insert suspend fun add(workout: Workout): Long
    @Delete suspend fun delete(routine: Routine)
    @Delete suspend fun delete(workout: Workout)
}

@Database(entities = [Routine::class, Workout::class], version = 3, exportSchema = false)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao

    companion object {
        /** A versão 2 guarda a duração do treino. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Workout ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** A versão 3 guarda a avaliação e a anotação do treino. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Workout ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Workout ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
