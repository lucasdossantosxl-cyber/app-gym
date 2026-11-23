package br.com.meutreino

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Barra de descanso presa ao rodapé. Aparece sozinha quando uma série é concluída e pode ser ajustada
 * sem sair do treino: −15 s, +15 s, pausar, encerrar ou tocar no tempo para escolher outra duração.
 */
@Composable
fun RestBar(model: TrainingModel, modifier: Modifier = Modifier, bottomInset: Boolean = false) {
    val timer = model.timer
    if (!timer.active) return
    var picking by remember { mutableStateOf(false) }
    val seconds = ((timer.remaining + 999) / 1000).toInt()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(timer.finished) {
        if (timer.finished) {
            delay(6000)
            model.stopRest()
        }
    }

    Surface(modifier = modifier.fillMaxWidth(),
        color = if (timer.finished) colors.primaryContainer else colors.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), shadowElevation = 12.dp) {
        Column(if (bottomInset) Modifier.navigationBarsPadding() else Modifier) {
            LinearProgressIndicator(
                progress = { if (timer.duration > 0) timer.remaining.toFloat() / timer.duration else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp), strokeCap = StrokeCap.Butt, gapSize = 0.dp,
                drawStopIndicator = {})
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Escolher tempo de descanso") { picking = true }
                    .padding(vertical = 2.dp)) {
                    Text(when {
                        timer.finished -> "Descanso concluído"
                        timer.paused -> "Descanso pausado"
                        timer.label.isNotBlank() -> "Descanso · ${timer.label}"
                        else -> "Descanso"
                    }, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    Text(clock(seconds), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = if (timer.finished) colors.onPrimaryContainer else colors.primary)
                }
                if (!timer.finished) {
                    FilledTonalButton(onClick = { model.adjustRest(-15) }, contentPadding = SmallPadding,
                        modifier = Modifier.semantics { contentDescription = "Tirar 15 segundos" }) { Text("−15") }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(onClick = { model.adjustRest(15) }, contentPadding = SmallPadding,
                        modifier = Modifier.semantics { contentDescription = "Somar 15 segundos" }) { Text("+15") }
                    IconButton(onClick = model::toggleRest) {
                        Icon(if (timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (timer.running) "Pausar descanso" else "Retomar descanso")
                    }
                }
                IconButton(onClick = model::stopRest) { Icon(Icons.Filled.Close, "Encerrar descanso") }
            }
        }
    }

    if (picking) {
        RestPicker(title = "Tempo de descanso", initial = (timer.duration / 1000).toInt().coerceAtLeast(5),
            confirm = "Iniciar", onConfirm = { model.startRest(it, timer.label) }, onDismiss = { picking = false },
            secondary = "Usar como padrão", onSecondary = {
                model.changeDefaultRest(it)
                model.startRest(it, timer.label)
            })
    }
}

private val SmallPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
private val presets = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)

/** Folha para escolher uma duração: atalhos prontos e ajuste fino de 5 em 5 segundos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestPicker(
    title: String, initial: Int, confirm: String, onConfirm: (Int) -> Unit, onDismiss: () -> Unit,
    secondary: String? = null, onSecondary: (Int) -> Unit = {}
) {
    var seconds by remember { mutableIntStateOf(initial.coerceIn(5, RestTimer.MAX_SECONDS)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { seconds = (seconds - 5).coerceAtLeast(5) }) {
                    Icon(Icons.Filled.Remove, "Tirar 5 segundos")
                }
                Text(clock(seconds), style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp))
                FilledTonalIconButton(onClick = { seconds = (seconds + 5).coerceAtMost(RestTimer.MAX_SECONDS) }) {
                    Icon(Icons.Filled.Add, "Somar 5 segundos")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    FilterChip(selected = seconds == preset, onClick = { seconds = preset }, label = { Text(clock(preset)) })
                }
            }
            Button(onClick = { onConfirm(seconds); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(confirm) }
            if (secondary != null) {
                OutlinedButton(onClick = { onSecondary(seconds); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(secondary)
                }
            }
        }
    }
}
