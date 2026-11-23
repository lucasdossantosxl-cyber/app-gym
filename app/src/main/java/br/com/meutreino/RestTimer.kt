package br.com.meutreino

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

class RestTimer {
    var duration by mutableLongStateOf(90_000)
        private set
    var remaining by mutableLongStateOf(duration)
        private set
    private var endsAt by mutableLongStateOf(0)
    val running get() = endsAt != 0L
    var finished by mutableStateOf(false)
        private set

    fun select(seconds: Long) {
        duration = seconds * 1000
        reset()
    }

    fun tick() {
        if (!running) return
        remaining = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (remaining == 0L) {
            endsAt = 0
            finished = true
        }
    }

    fun toggle() {
        if (running) {
            tick()
            endsAt = 0
        } else {
            if (remaining == 0L) reset()
            endsAt = SystemClock.elapsedRealtime() + remaining
        }
    }

    fun reset() {
        endsAt = 0
        remaining = duration
        finished = false
    }
}

@Composable
fun RestScreen(timer: RestTimer) {
    val seconds = (timer.remaining + 999) / 1000
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (timer.finished) "Descanso concluído" else "Tempo restante")
            Text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            LinearProgressIndicator(progress = { timer.remaining.toFloat() / timer.duration },
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60L, 90L, 120L).forEach { seconds ->
                    FilterChip(selected = timer.duration == seconds * 1000,
                        onClick = { timer.select(seconds) }, label = { Text("$seconds s") })
                }
            }
            Button(onClick = timer::toggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (timer.running) "Pausar" else "Iniciar")
            }
            OutlinedButton(onClick = timer::reset, modifier = Modifier.fillMaxWidth()) { Text("Zerar") }
        }
    }
}
