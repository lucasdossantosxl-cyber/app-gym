package br.com.meutreino

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
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
    val entries: String
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM Routine ORDER BY name COLLATE NOCASE, id")
    fun routines(): Flow<List<Routine>>

    @Query("SELECT * FROM Workout ORDER BY date DESC, id DESC")
    fun workouts(): Flow<List<Workout>>

    @Upsert suspend fun save(routine: Routine)
    @Upsert suspend fun save(workout: Workout)
}

@Database(entities = [Routine::class, Workout::class], version = 1, exportSchema = false)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
}
