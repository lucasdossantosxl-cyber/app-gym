package br.com.meutreino

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Abre um vídeo de execução: o link salvo no exercício ou uma busca no YouTube pelo nome. */
fun openVideo(context: Context, name: String, link: String = "") {
    val custom = link.trim().takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    val url = custom ?: ("https://www.youtube.com/results?search_query=" +
        Uri.encode("como fazer $name execução correta"))
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Lista da biblioteca com busca e filtro por grupo muscular. Com [selected] nulo, serve só para consulta;
 * com uma lista, vira seletor de múltiplos exercícios.
 */
@Composable
fun ExerciseList(
    catalog: Catalog,
    custom: List<Exercise>,
    selected: List<Exercise>?,
    onToggle: (Exercise) -> Unit,
    onInfo: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp)
) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val mine = "Meus"
    val results = remember(query, group, custom) {
        val words = query.normalized().split(' ').filter { it.isNotEmpty() }
        val own = if (group == null || group == mine) {
            custom.filter { exercise -> words.all { exercise.name.normalized().contains(it) } }
        } else emptyList()
        own + if (group == mine) emptyList() else catalog.search(query, group).map { it.toExercise() }
    }
    val canCreate = selected != null && query.trim().length >= 3 &&
        results.none { it.name.normalized() == query.normalized() }

    Column(modifier) {
        OutlinedTextField(value = query, onValueChange = { query = it.take(60) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar exercício, músculo ou equipamento") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isEmpty()) null else {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Limpar busca") } }
            },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("Todos") }) }
            if (custom.isNotEmpty()) item {
                FilterChip(selected = group == mine, onClick = { group = if (group == mine) null else mine },
                    label = { Text(mine) })
            }
            items(catalog.groups) { name ->
                FilterChip(selected = group == name, onClick = { group = if (group == name) null else name },
                    label = { Text(name) })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = contentPadding) {
            if (canCreate) item(key = "criar") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Não está na lista? Crie “${query.trim()}”:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            onToggle(Exercise(name = query.trim(), target = "12", increment = "2,5"))
                            query = ""
                        }) { Text("Criar como força") }
                        FilledTonalButton(onClick = {
                            onToggle(Exercise(name = query.trim(), cardio = true, unit = "km", sets = "1"))
                            query = ""
                        }) { Text("Criar como cardio") }
                    }
                }
            }
            if (results.isEmpty() && !canCreate) item(key = "vazio") {
                Text("Nenhum exercício encontrado.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(results, key = { it.key() + (if (it.cardio) "/c" else "/f") }) { exercise ->
                val item = catalog[exercise.ref]
                val chosen = selected?.any { it.key() == exercise.key() } == true
                Row(Modifier.fillMaxWidth()
                    .clickable { if (selected == null) onInfo(exercise) else onToggle(exercise) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ExercisePhoto(exercise.ref, exercise.name, Modifier.size(width = 76.dp, height = 54.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(item?.let { "${it.group} · ${it.equipment}" } ?: if (exercise.cardio) "Meu exercício · Cardio" else "Meu exercício",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected != null) {
                        IconButton(onClick = { onInfo(exercise) }) { Icon(Icons.Outlined.Info, "Como fazer ${exercise.name}") }
                        Surface(shape = CircleShape, modifier = Modifier.size(28.dp),
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest) {
                            if (chosen) Icon(Icons.Filled.Check, "Selecionado", Modifier.padding(4.dp),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

/** Tela cheia para escolher um ou mais exercícios da biblioteca ou criar um novo. */
@Composable
fun ExercisePicker(model: TrainingModel, custom: List<Exercise>, onAdd: (List<Exercise>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(emptyList<Exercise>()) }
    var info by remember { mutableStateOf<Exercise?>(null) }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(title = { Text("Adicionar exercícios") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Fechar") } })
            },
            bottomBar = {
                Button(onClick = { onAdd(selected); onDismiss() }, enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(when (selected.size) {
                        0 -> "Selecione os exercícios"
                        1 -> "Adicionar 1 exercício"
                        else -> "Adicionar ${selected.size} exercícios"
                    })
                }
            }
        ) { padding ->
            ExerciseList(model.catalog, custom, selected,
                onToggle = { exercise ->
                    selected = if (selected.any { it.key() == exercise.key() }) selected.filterNot { it.key() == exercise.key() }
                    else selected + exercise
                },
                onInfo = { info = it }, modifier = Modifier.padding(padding))
        }
    }
    info?.let { ExerciseSheet(it, model.catalog, stats = null, onDismiss = { info = null }) }
}

/** Folha com as fotos do movimento, o passo a passo, o vídeo e, quando há histórico, a evolução. */
@Composable
fun ExerciseSheet(exercise: Exercise, catalog: Catalog, stats: ExerciseStats?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val item = catalog[exercise.ref]
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(item?.let { "${it.group} · ${it.equipment}" } ?: if (exercise.cardio) "Meu exercício · Cardio" else "Meu exercício",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item != null) {
                // alterna a foto inicial e a final para mostrar o movimento
                var frame by remember { mutableIntStateOf(0) }
                LaunchedEffect(item.id) {
                    while (true) {
                        delay(1100)
                        frame = 1 - frame
                    }
                }
                ExercisePhoto(item.id, item.name, Modifier.fillMaxWidth().aspectRatio(1.5f), frame = frame, thumb = false)
                Text("Como fazer", style = MaterialTheme.typography.titleMedium)
                Text(item.how, style = MaterialTheme.typography.bodyLarge)
            }
            if (exercise.note.isNotBlank()) {
                Text("Observação: ${exercise.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { openVideo(context, exercise.name, exercise.video) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (exercise.video.isBlank()) "Ver vídeos de execução no YouTube" else "Abrir vídeo salvo")
            }
            if (stats != null && stats.points.isNotEmpty()) ProgressSection(stats)
        }
    }
}

/** Recordes e gráfico de evolução de um exercício. */
@Composable
fun ProgressSection(stats: ExerciseStats) {
    var metric by rememberSaveable { mutableStateOf(Metric.Load) }
    var picked by remember(stats.key, metric) { mutableStateOf<Int?>(null) }
    val unit = if (stats.cardio) "min" else stats.unit
    Text("Evolução", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stats.cardio) {
            Record("Maior tempo", "${stats.bestLoad.pretty()} min", Modifier.weight(1f))
            Record("Maior distância", "${stats.bestVolume.pretty()} km", Modifier.weight(1f))
            Record("Treinos", "${stats.points.size}", Modifier.weight(1f))
        } else {
            Record("Maior carga", "${stats.bestLoad.pretty()} ${stats.unit}", Modifier.weight(1f))
            Record("1RM estimado", "${stats.bestOneRepMax.pretty()} ${stats.unit}", Modifier.weight(1f))
            Record("Treinos", "${stats.points.size}", Modifier.weight(1f))
        }
    }
    if (!stats.cardio) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.entries.forEach { option ->
                FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(option.label) })
            }
        }
    }
    val shown = if (stats.cardio) Metric.Load else metric
    if (stats.points.size < 2) {
        Text("O gráfico aparece a partir do segundo treino com este exercício.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        ProgressChart(stats.points, shown, picked, onPick = { picked = it },
            modifier = Modifier.fillMaxWidth().height(190.dp))
    }
    val point = picked?.let { stats.points.getOrNull(it) } ?: stats.points.last()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text((if (picked == null) "Último treino · " else "") + point.date.dayLabel(),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(point.summary, style = MaterialTheme.typography.bodyLarge)
            if (!stats.cardio) Text("${shown.label}: ${shown.of(point).pretty()} $unit",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Record(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Gráfico de linha simples: um ponto por treino, com o recorde em destaque. Toque em um ponto para ver o treino. */
@Composable
fun ProgressChart(points: List<Point>, metric: Metric, picked: Int?, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val values = remember(points, metric) { points.map { metric.of(it) } }
    val low = values.min()
    val high = values.max()
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val record = values.indexOf(high)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(high.pretty(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text("${points.size} treinos", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp)
            .pointerInput(points.size) {
                detectTapGestures { tap ->
                    val step = if (points.size > 1) size.width.toFloat() / (points.size - 1) else 0f
                    val index = if (step == 0f) 0 else ((tap.x / step) + 0.5f).toInt().coerceIn(0, points.size - 1)
                    onPick(if (picked == index) null else index)
                }
            }) {
            val inset = 8.dp.toPx()
            val width = size.width - inset * 2
            val height = size.height - inset * 2
            fun at(index: Int) = Offset(
                inset + if (values.size > 1) width * index / (values.size - 1) else width / 2,
                inset + (height * (1 - (values[index] - low) / span)).toFloat())
            for (line in 0..2) {
                val y = inset + height * line / 2
                drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val path = Path().apply {
                values.indices.forEach { index ->
                    val point = at(index)
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(path, colors.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (values.size <= 40) values.indices.forEach { drawCircle(colors.primary, 3.5.dp.toPx(), at(it)) }
            drawCircle(colors.tertiary, 6.dp.toPx(), at(record))
            picked?.takeIf { it in values.indices }?.let { index ->
                val point = at(index)
                drawLine(colors.onSurfaceVariant, Offset(point.x, 0f), Offset(point.x, size.height), strokeWidth = 1.dp.toPx())
                drawCircle(colors.onSurface, 6.dp.toPx(), point)
                drawCircle(colors.primary, 4.dp.toPx(), point)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(low.pretty() + " · " + points.first().date.dayLabel(), style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant)
            Text(points.last().date.dayLabel(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
