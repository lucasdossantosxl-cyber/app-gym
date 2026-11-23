package br.com.meutreino

import org.json.JSONArray
import org.json.JSONObject

/** Cópia de segurança em JSON com todas as rotinas e todos os treinos. */
object Backup {
    private const val APP = "meutreino"

    class Data(val routines: List<Routine>, val workouts: List<Workout>)

    fun looksLikeBackup(text: String) = text.trimStart().startsWith("{") && text.contains("\"$APP\"")

    fun encode(routines: List<Routine>, workouts: List<Workout>): String = JSONObject()
        .put("app", APP).put("version", 1)
        .put("routines", JSONArray(routines.map {
            JSONObject().put("id", it.id).put("name", it.name).put("exercises", JSONArray(it.exercises))
        }))
        .put("workouts", JSONArray(workouts.map {
            JSONObject().put("routineId", it.routineId ?: JSONObject.NULL).put("name", it.name)
                .put("date", it.date).put("duration", it.duration).put("entries", JSONArray(it.entries))
        }))
        .toString(1)

    /** Lê e valida um backup. Lança exceção quando o arquivo não é um backup do Meu Treino. */
    fun decode(text: String): Data {
        val root = JSONObject(text)
        require(root.optString("app") == APP) { "Arquivo de outro aplicativo" }
        val routines = root.getJSONArray("routines").let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val exercises = item.getJSONArray("exercises").toString()
                exercises.decodeExercises()
                Routine(item.getLong("id"), item.getString("name").take(200), exercises)
            }
        }
        val workouts = root.getJSONArray("workouts").let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val entries = item.getJSONArray("entries").toString()
                entries.decodeEntries()
                Workout(0, if (item.isNull("routineId")) null else item.getLong("routineId"),
                    item.getString("name").take(200), item.getLong("date"), entries,
                    item.optLong("duration").coerceAtLeast(0))
            }
        }
        return Data(routines, workouts)
    }

    /**
     * Junta o backup ao que já existe no aparelho, sem duplicar o que já está lá.
     * Devolve quantas rotinas e quantos treinos foram acrescentados.
     */
    suspend fun restore(dao: TrainingDao, data: Data): Pair<Int, Int> {
        val existingRoutines = dao.allRoutines()
        val existingWorkouts = dao.allWorkouts().map { it.name to it.date }.toSet()
        val ids = HashMap<Long, Long>()
        var routines = 0
        for (routine in data.routines) {
            val same = existingRoutines.firstOrNull { it.name == routine.name && it.exercises == routine.exercises }
            ids[routine.id] = same?.id ?: dao.add(routine.copy(id = 0)).also { routines++ }
        }
        var workouts = 0
        for (workout in data.workouts) {
            if ((workout.name to workout.date) in existingWorkouts) continue
            dao.add(workout.copy(id = 0, routineId = workout.routineId?.let { ids[it] }))
            workouts++
        }
        return routines to workouts
    }
}
