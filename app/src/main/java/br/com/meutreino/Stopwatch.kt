package br.com.meutreino

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cronômetro com voltas. Guarda o instante em que começou a contar, em vez de somar tiques, para seguir
 * certo com a tela apagada ou com o app em segundo plano.
 */
class Stopwatch(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private var startedAt by mutableLongStateOf(0L)
    private var accumulated by mutableLongStateOf(0L)
    var running by mutableStateOf(false)
        private set
    /** Tempo total, em milissegundos, no momento em que cada volta foi marcada. */
    var marks by mutableStateOf(emptyList<Long>())
        private set

    /** Tempo total decorrido, em milissegundos. */
    fun elapsed(): Long = accumulated + if (running) now() - startedAt else 0L

    /** Tempo da volta em andamento. */
    fun currentLap(): Long = elapsed() - (marks.lastOrNull() ?: 0L)

    /** Duração de cada volta marcada, da primeira para a última. */
    fun laps(): List<Long> = marks.mapIndexed { index, mark -> mark - (marks.getOrNull(index - 1) ?: 0L) }

    fun start() {
        if (running) return
        startedAt = now()
        running = true
    }

    fun pause() {
        if (!running) return
        accumulated = elapsed()
        running = false
    }

    fun lap() {
        if (!running || marks.size >= MAX_LAPS) return
        marks = marks + elapsed()
    }

    fun reset() {
        running = false
        accumulated = 0
        marks = emptyList()
    }

    /** Estado para guardar entre aberturas do app. [wall] é o relógio do sistema, que sobrevive ao processo. */
    fun snapshot(wall: Long): String = JSONObject()
        .put("elapsed", elapsed()).put("running", running).put("wall", wall).put("marks", JSONArray(marks)).toString()

    fun restore(text: String, wall: Long) {
        runCatching {
            val saved = JSONObject(text)
            val list = saved.getJSONArray("marks")
            val wasRunning = saved.getBoolean("running")
            // enquanto o app esteve fechado, o cronômetro em andamento continuou contando
            val away = if (wasRunning) (wall - saved.getLong("wall")).coerceAtLeast(0) else 0L
            accumulated = (saved.getLong("elapsed") + away).coerceIn(0, MAX_MILLIS)
            marks = List(list.length()) { list.getLong(it) }.filter { it in 0..accumulated }.sorted().take(MAX_LAPS)
            running = false
            if (wasRunning && accumulated < MAX_MILLIS) start()
        }
    }

    companion object {
        const val MAX_LAPS = 99
        const val MAX_MILLIS = 100L * 3600 * 1000
    }
}

/** 83450 ms viram "01:23" e "45" (centésimos); com horas, "1:01:23". */
fun stopwatchClock(millis: Long): Pair<String, String> {
    val total = millis.coerceAtLeast(0)
    val seconds = total / 1000
    val main = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
    return main to "%02d".format(total % 1000 / 10)
}

fun stopwatchLabel(millis: Long): String = stopwatchClock(millis).let { "${it.first},${it.second}" }
