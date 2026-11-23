package br.com.meutreino

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
                .put("date", it.date).put("duration", it.duration).put("rating", it.rating).put("note", it.note)
                .put("entries", JSONArray(it.entries))
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
                    item.optLong("duration").coerceAtLeast(0), item.optInt("rating").coerceIn(0, 5),
                    item.optString("note").take(500))
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

/**
 * Cópia automática em Downloads/MeuTreino. O arquivo fica fora da área do app, então continua lá depois de
 * desinstalar. Usa a MediaStore do Android 10 em diante, que não exige permissão para gravar em Downloads.
 */
object AutoBackup {
    const val FOLDER = "Download/MeuTreino"
    const val NAME = "meu-treino-backup.json"
    val supported get() = Build.VERSION.SDK_INT >= 29

    /** Grava o backup, reaproveitando o arquivo anterior quando ele ainda existe. Devolve o endereço usado. */
    fun write(context: Context, json: String, saved: String?): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val resolver = context.contentResolver
        val bytes = json.toByteArray()
        if (saved != null) {
            val reused = runCatching {
                resolver.openOutputStream(Uri.parse(saved), "wt")?.use { it.write(bytes) } != null
            }.getOrDefault(false)
            if (reused) return saved
        }
        return runCatching {
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MeuTreino")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }) ?: return null
            try {
                resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                uri.toString()
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }.getOrNull()
    }
}
