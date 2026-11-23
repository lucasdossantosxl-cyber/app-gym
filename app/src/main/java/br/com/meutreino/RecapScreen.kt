package br.com.meutreino

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** Retrospectiva do mês ou do ano: quantas vezes treinou, cardio, exercícios mais feitos e recordes. */
@Composable
fun RecapTab(sessions: List<Session>) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var yearly by rememberSaveable { mutableStateOf(false) }
    // quantos períodos (meses ou anos) para trás em relação ao atual
    var back by rememberSaveable { mutableIntStateOf(0) }
    val from = if (yearly) LocalDate.of(today.year - back, 1, 1) else YearMonth.from(today).minusMonths(back.toLong()).atDay(1)
    val to = if (yearly) from.plusYears(1) else from.plusMonths(1)
    val previous = if (yearly) from.minusYears(1) else from.minusMonths(1)
    val recap = remember(sessions, from, to) { sessions.recap(from, to, previous) }
    val oldest = remember(sessions) { sessions.minOfOrNull { it.workout.date }?.localDate() ?: today }
    val title = if (yearly) "${from.year}" else monthFormat.format(from).replaceFirstChar { it.uppercase() }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !yearly, onClick = { yearly = false; back = 0 }, label = { Text("Mês") })
            FilterChip(selected = yearly, onClick = { yearly = true; back = 0 }, label = { Text("Ano") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { back++ }, enabled = from.isAfter(oldest)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Período anterior")
            }
            IconButton(onClick = { back-- }, enabled = back > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo período")
            }
        }

        // abertura: o número que resume o período
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F6B45), Color(0xFF123B4A), Color(0xFF3A2A5C))))
            .padding(22.dp)) {
            Column {
                Text("Sua retrospectiva", color = Color(0xFFCDEBD8), style = MaterialTheme.typography.labelLarge)
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                if (recap.empty) {
                    Text("Nenhum treino neste período.", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Conclua um treino e ele entra aqui.", color = Color(0xFFCDEBD8))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${recap.workouts}", color = Color(0xFFA8F5BC), fontSize = 72.sp, fontWeight = FontWeight.Black,
                            lineHeight = 72.sp)
                        Text(if (recap.workouts == 1) "treino" else "treinos", color = Color.White,
                            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
                    }
                    Text("em ${if (recap.days == 1) "1 dia" else "${recap.days} dias"}" +
                        (if (recap.seconds > 0) " · ${durationLabel(recap.seconds)} treinando" else ""), color = Color.White)
                    compare(recap.workouts, recap.previousWorkouts, yearly)?.let {
                        Text(it, color = Color(0xFFFFE2A8), modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (!recap.empty) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Carga levantada", if (recap.volume > 0) recap.volume.pretty() else "0", "kg somando todas as séries",
                    Color(0xFF1D3B2C), Modifier.weight(1f))
                Tile("Séries", "${recap.sets}", if (recap.exerciseCount == 1) "em 1 exercício" else "em ${recap.exerciseCount} exercícios",
                    Color(0xFF2B2F4A), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Dias seguidos", "${recap.streak}", if (recap.streak == 1) "foi a maior sequência" else "na maior sequência",
                    Color(0xFF4A3A12), Modifier.weight(1f))
                val day = recap.weekday
                Tile("Dia preferido", day?.getDisplayName(TextStyle.SHORT, PtBr)?.trimEnd('.')?.replaceFirstChar { it.uppercase() } ?: "–",
                    if (recap.weekdayCount == 1) "1 treino nesse dia" else "${recap.weekdayCount} treinos nesse dia",
                    Color(0xFF3A2440), Modifier.weight(1f))
            }

            if (recap.cardioMinutes > 0) Panel {
                Text("Cardio", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Number(recap.cardioMinutes.pretty(), "minutos", Modifier.weight(1f))
                    Number(if (recap.cardioDistance > 0) recap.cardioDistance.pretty() else "–", "km", Modifier.weight(1f))
                    Number(if (recap.calories > 0) recap.calories.pretty() else "–", "kcal", Modifier.weight(1f))
                }
                Text(if (recap.cardioSessions == 1) "1 treino teve cardio." else "${recap.cardioSessions} treinos tiveram cardio.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }

            if (recap.topExercises.isNotEmpty()) Panel {
                Text("Exercícios mais feitos", style = MaterialTheme.typography.titleMedium)
                recap.topExercises.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", Modifier.width(26.dp), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black, color = if (index == 0) colors.tertiary else colors.onSurfaceVariant)
                        ExercisePhoto(item.ref, item.name, Modifier.size(width = 60.dp, height = 44.dp))
                        Text(item.name, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (item.count == 1) "1 vez" else "${item.count} vezes", color = colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (recap.records.isNotEmpty()) Panel {
                Text("Recordes de carga", style = MaterialTheme.typography.titleMedium)
                recap.records.forEach { record ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(record.name, Modifier.weight(1f).padding(end = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${record.before.pretty()} → ${record.now.pretty()} ${record.unit}", color = colors.tertiary,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Panel {
                Text("Mais do período", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (recap.topRoutine.isNotEmpty()) Fact("Treino mais repetido",
                    "${recap.topRoutine} (${if (recap.topRoutineCount == 1) "1 vez" else "${recap.topRoutineCount} vezes"})")
                recap.longest?.let { Fact("Treino mais longo", "${durationLabel(it.workout.duration)} em ${it.workout.date.dayLabel()}") }
                if (recap.rated > 0) Fact("Avaliação média", "%.1f de 5 em %s".format(PtBr, recap.averageRating,
                    if (recap.rated == 1) "1 treino avaliado" else "${recap.rated} treinos avaliados"))
                if (recap.newExercises > 0) Fact("Exercícios novos",
                    if (recap.newExercises == 1) "1 que você nunca tinha feito" else "${recap.newExercises} que você nunca tinha feito")
                if (recap.previousVolume > 0 && recap.volume > 0) Fact("Carga em relação ao período anterior",
                    percent(recap.volume, recap.previousVolume))
            }

            FilledTonalButton(onClick = {
                val text = recap.shareText(title)
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text), "Compartilhar retrospectiva"))
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar retrospectiva")
            }
        }
    }
}

private fun compare(now: Int, before: Int, yearly: Boolean): String? {
    val period = if (yearly) "ano anterior" else "mês anterior"
    return when {
        before == 0 -> null
        now > before -> "${now - before} a mais que no $period"
        now < before -> "${before - now} a menos que no $period"
        else -> "Mesmo número do $period"
    }
}

private fun percent(now: Double, before: Double): String {
    val change = Math.round((now / before - 1) * 100)
    return when {
        change > 0 -> "+$change%"
        change < 0 -> "−${-change}%"
        else -> "igual"
    }
}

@Composable
private fun Tile(label: String, value: String, caption: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(color).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFFD5DED8), maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD5DED8))
    }
}

@Composable
private fun Number(value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
