package br.com.meutreino

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Histórico em quatro abas: lista de treinos, evolução por exercício, resumo com calendário e retrospectiva. */
@Composable
fun HistoryScreen(model: TrainingModel, sessions: List<Session>, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Text("Histórico", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        PrimaryTabRow(selectedTabIndex = page, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Treinos", "Exercícios", "Resumo", "Meu mês").forEachIndexed { index, title ->
                Tab(selected = page == index, onClick = { page = index }, text = { Text(title) })
            }
        }
        if (sessions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhum treino registrado ainda", style = MaterialTheme.typography.titleMedium)
                Text("Conclua um treino na aba Treinar e ele aparece aqui, com a evolução de cada exercício.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp))
            }
        } else when (page) {
            0 -> WorkoutsTab(model, sessions)
            1 -> ExercisesTab(model, sessions)
            2 -> SummaryTab(sessions)
            else -> RecapTab(sessions)
        }
    }
}

val monthFormat = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PtBr)

@Composable
private fun WorkoutsTab(model: TrainingModel, sessions: List<Session>) {
    var open by rememberSaveable { mutableLongStateOf(-1L) }
    var deleting by remember { mutableStateOf<Session?>(null) }
    val months = remember(sessions) { sessions.groupBy { YearMonth.from(it.workout.date.localDate()) }.toList() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        months.forEach { (month, list) ->
            item(key = "m$month") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(monthFormat.format(month).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (list.size == 1) "1 treino" else "${list.size} treinos",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.workout.id }) { session ->
                val expanded = open == session.workout.id
                Surface(onClick = { open = if (expanded) -1L else session.workout.id }, shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(session.workout.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (session.workout.rating > 0) RatingStars(session.workout.rating, onChange = null, size = 16)
                        }
                        Text(listOfNotNull(
                            "${session.workout.date.dayLabel()} às ${session.workout.date.timeLabel()}",
                            durationLabel(session.workout.duration).takeIf { it.isNotEmpty() },
                            when (session.setCount) {
                                0 -> "sem séries registradas"
                                1 -> "1 série"
                                else -> "${session.setCount} séries"
                            },
                            session.volume.takeIf { it > BigDecimal.ZERO }?.let { "${it.pretty()} kg no total" }
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val shown = if (expanded) session.entries else session.entries.take(4)
                        shown.forEach { entry ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(entry.exercise.name, Modifier.weight(0.45f), style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
                                Text(if (expanded) entry.summary() else entry.brief(), Modifier.weight(0.55f).padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) 6 else 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (!expanded && session.entries.size > 4) {
                            Text("+ ${session.entries.size - 4} exercícios", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (session.workout.note.isNotBlank()) {
                            Text("“${session.workout.note}”", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 8 else 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                        if (expanded) Row {
                            TextButton(onClick = { model.edit(session) }) { Text("Editar") }
                            TextButton(onClick = { deleting = session }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { session ->
        Confirm("Excluir registro?", "“${session.workout.name}” de ${session.workout.date.dayLabel()} será apagado do histórico.",
            confirm = "Excluir", onConfirm = { deleting = null; model.delete(session.workout) }, onDismiss = { deleting = null })
    }
}

/** Resumo de uma linha: "3 séries · melhor 40 kg × 12". */
private fun Entry.brief(): String {
    if (exercise.cardio) return summary()
    val best = sets.maxByOrNull { it.first.decimal() ?: BigDecimal.ZERO } ?: return ""
    return "${if (sets.size == 1) "1 série" else "${sets.size} séries"} · melhor ${best.first} ${exercise.unit} × ${best.second}"
}

@Composable
private fun ExercisesTab(model: TrainingModel, sessions: List<Session>) {
    val stats = remember(sessions) { sessions.exerciseStats() }
    var open by remember { mutableStateOf<ExerciseStats?>(null) }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(stats, key = { it.key }) { item ->
            Surface(onClick = { open = item }, color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExercisePhoto(item.ref, item.name, Modifier.size(width = 76.dp, height = 54.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val count = if (item.points.size == 1) "1 treino" else "${item.points.size} treinos"
                        Text(when {
                            item.cardio -> "$count · maior tempo ${item.bestLoad.pretty()} min"
                            item.bestLoad > 0 -> "$count · maior carga ${item.bestLoad.pretty()} ${item.unit}"
                            else -> "$count · sem carga"
                        },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Trend(item)
                }
            }
        }
    }
    open?.let { item ->
        ExerciseSheet(Exercise(name = item.name, cardio = item.cardio, unit = item.unit, ref = item.ref), model.catalog,
            stats = item, onDismiss = { open = null })
    }
}

/** Diferença de carga máxima entre o primeiro e o último treino do exercício. */
@Composable
private fun Trend(stats: ExerciseStats) {
    if (stats.cardio || stats.points.size < 2) return
    val delta = stats.points.last().best - stats.points.first().best
    if (delta == 0.0) return
    Text((if (delta > 0) "+" else "−") + kotlin.math.abs(delta).pretty() + " " + stats.unit,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        color = if (delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
}

@Composable
private fun SummaryTab(sessions: List<Session>) {
    val today = remember { LocalDate.now() }
    val days = remember(sessions) { sessions.map { it.workout.date.localDate() } }
    val trained = remember(days) { days.toSet() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeek = days.count { !it.isBefore(weekStart) }
    val thisMonth = days.count { YearMonth.from(it) == YearMonth.from(today) }
    val streak = remember(trained) {
        // semanas seguidas com pelo menos um treino, contando a atual se já houve treino nela
        val weeks = trained.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var cursor = if (weekStart in weeks) weekStart else weekStart.minusWeeks(1)
        var count = 0
        while (cursor in weeks) {
            count++
            cursor = cursor.minusWeeks(1)
        }
        count
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Esta semana", "$thisWeek", if (thisWeek == 1) "treino" else "treinos", Modifier.weight(1f))
            Stat("Este mês", "$thisMonth", if (thisMonth == 1) "treino" else "treinos", Modifier.weight(1f))
            Stat("Sequência", "$streak", if (streak == 1) "semana" else "semanas", Modifier.weight(1f))
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior")
                }
                Text(monthFormat.format(month).replaceFirstChar { it.uppercase() }, Modifier.weight(1f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "T", "Q", "Q", "S", "S", "D").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    for (column in 0..6) {
                        val day = week * 7 + column - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val done = date in trained
                                Box(Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                                    .then(if (date == today) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center) {
                                    Text("$day", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                                        color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        Panel {
            Text("Treinos por semana", style = MaterialTheme.typography.titleMedium)
            val weeks = (7 downTo 0).map { weekStart.minusWeeks(it.toLong()) }
            val counts = weeks.map { start -> days.count { !it.isBefore(start) && it.isBefore(start.plusWeeks(1)) } }
            val top = counts.max().coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                counts.forEachIndexed { index, count ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom) {
                        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().fillMaxHeight((0.08f + 0.72f * count / top))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (index == counts.lastIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("há 7 semanas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("esta semana", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Panel {
            Text("Total acumulado", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val seconds = sessions.sumOf { it.workout.duration }
            val volume = sessions.fold(BigDecimal.ZERO) { sum, session -> sum + session.volume }
            Line("Treinos registrados", "${sessions.size}")
            Line("Séries registradas", sessions.sumOf { it.setCount }.toDouble().pretty())
            if (seconds > 0) Line("Tempo de treino", durationLabel(seconds))
            Line("Carga levantada", "${volume.pretty()} kg")
            val cardio = sessions.sumOf { it.cardioMinutes }
            if (cardio > 0) Line("Cardio", "${cardio.pretty()} min" +
                sessions.sumOf { it.cardioDistance }.takeIf { it > 0 }?.let { " · ${it.pretty()} km" }.orEmpty())
            val calories = sessions.sumOf { it.calories }
            if (calories > 0) Line("Calorias no cardio", "${calories.pretty()} kcal")
            val rated = sessions.filter { it.workout.rating > 0 }
            if (rated.isNotEmpty()) Line("Avaliação média",
                "%.1f de 5".format(PtBr, rated.sumOf { it.workout.rating }.toDouble() / rated.size))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
