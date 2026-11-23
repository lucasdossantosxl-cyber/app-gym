package br.com.meutreino

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Aba Cronômetro: tempo grande dentro de um anel, voltas e os três botões de um relógio de celular. */
@Composable
fun StopwatchScreen(model: TrainingModel, modifier: Modifier = Modifier) {
    val watch = model.stopwatch
    val colors = MaterialTheme.colorScheme
    // redesenha a cada quadro só enquanto o cronômetro corre
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(watch.running) {
        while (watch.running) withFrameMillis { frame = it }
    }
    val elapsed = frame.let { watch.elapsed() }
    val laps = watch.laps()
    val (main, cents) = stopwatchClock(elapsed)

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Cronômetro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp))

        Box(Modifier.weight(if (laps.isEmpty()) 1f else 0.9f).widthIn(max = 320.dp).padding(horizontal = 36.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val stroke = 8.dp.toPx()
                val inset = stroke * 1.5f
                val area = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(colors.surfaceContainerHighest, 0f, 360f, false, Offset(inset, inset), area, style = Stroke(stroke))
                // o anel dá uma volta por minuto, como o ponteiro dos segundos
                val sweep = (elapsed % 60_000) / 60_000f * 360f
                if (elapsed > 0) {
                    drawArc(colors.primary, -90f, sweep, false, Offset(inset, inset), area,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    val angle = Math.toRadians((sweep - 90).toDouble())
                    val radius = area.width / 2
                    drawCircle(colors.onPrimaryContainer, stroke * 0.9f,
                        Offset(size.width / 2 + radius * cos(angle).toFloat(), size.height / 2 + radius * sin(angle).toFloat()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(main, fontSize = if (main.length > 5) 44.sp else 56.sp, fontWeight = FontWeight.Light,
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"))
                    Text(",$cents", fontSize = 26.sp, color = colors.primary, modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
                }
                if (laps.isNotEmpty()) Text("Volta ${laps.size + 1} · ${stopwatchLabel(elapsed - watch.marks.last())}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
            }
        }

        if (laps.isNotEmpty()) {
            val fastest = laps.min()
            val slowest = laps.max()
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 4.dp)) {
                itemsIndexed(laps.asReversed(), key = { index, _ -> laps.size - index }) { index, lap ->
                    val number = laps.size - index
                    val color = when {
                        laps.size < 2 -> colors.onSurface
                        lap == fastest -> colors.primary
                        lap == slowest -> colors.error
                        else -> colors.onSurface
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Volta $number", Modifier.weight(1f), color = colors.onSurfaceVariant)
                        Text(stopwatchLabel(lap), Modifier.weight(1f), textAlign = TextAlign.Center, color = color,
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                        Text(stopwatchLabel(watch.marks[number - 1]), Modifier.weight(1f), textAlign = TextAlign.End,
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(onClick = { model.stopwatch { reset() } }, enabled = elapsed > 0, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Refresh, "Zerar")
            }
            LargeFloatingActionButton(onClick = { model.stopwatch { if (running) pause() else start() } }, shape = CircleShape,
                containerColor = colors.primary, contentColor = colors.onPrimary) {
                Icon(if (watch.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (watch.running) "Pausar" else "Iniciar", Modifier.size(40.dp))
            }
            FilledTonalIconButton(onClick = { model.stopwatch { lap() } }, enabled = watch.running, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Flag, "Marcar volta")
            }
        }
    }
}
