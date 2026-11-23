package br.com.meutreino

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Tela de registro. Com [live] verdadeiro é o treino em andamento: cada série tem um botão de concluir que
 * dispara o descanso. Com [live] falso edita um registro do histórico.
 */
@Composable
fun WorkoutScreen(model: TrainingModel, draft: WorkoutDraft, live: Boolean, sessions: List<Session>) {
    val change: (WorkoutDraft) -> Unit = { if (live) model.activeWorkout = it else model.recordDraft = it }
    val focus = LocalFocusManager.current
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Exercise?>(null) }
    var restFor by remember { mutableStateOf<Int?>(null) }
    var variantFor by remember { mutableStateOf<Int?>(null) }
    var finishing by remember { mutableStateOf(false) }
    val custom = remember(sessions) { sessions.customExercises() }
    val uniqueIds = draft.entries.map { it.exercise.id }.distinct().size == draft.entries.size

    BackHandler(enabled = !picking) {
        focus.clearFocus()
        if (live) model.workoutOpen = false else confirm = true
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (live) "Treino em andamento" else "Editar registro", style = MaterialTheme.typography.titleMedium)
                            if (live) Elapsed(draft.date) else Text(draft.date.dayLabel(),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            focus.clearFocus()
                            if (live) model.workoutOpen = false else confirm = true
                        }) {
                            if (live) Icon(Icons.Filled.KeyboardArrowDown, "Recolher treino") else Icon(Icons.Filled.Close, "Fechar")
                        }
                    },
                    actions = {
                        if (live) IconButton(onClick = { model.startRest(model.defaultRest) }) {
                            Icon(Icons.Filled.Timer, "Iniciar descanso")
                        }
                        Button(enabled = !model.busy, modifier = Modifier.padding(end = 8.dp), onClick = {
                            focus.clearFocus()
                            if (live) finishing = true else model.saveRecord()
                        }) { Text(if (live) "Concluir" else "Salvar") }
                    })
            },
            bottomBar = { RestBar(model, bottomInset = true) }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "nome") {
                    OutlinedTextField(value = draft.name, onValueChange = { change(draft.copy(name = it.take(60))) },
                        label = { Text("Nome do treino") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp))
                }
                if (model.error.isNotEmpty()) item(key = "erro") {
                    Text(model.error, color = MaterialTheme.colorScheme.error)
                }
                if (!live) item(key = "avaliacao") {
                    Panel {
                        Text("Avaliação do treino", style = MaterialTheme.typography.titleMedium)
                        RatingStars(draft.rating, onChange = { change(draft.copy(rating = if (draft.rating == it) 0 else it)) },
                            modifier = Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(value = draft.note, onValueChange = { change(draft.copy(note = it.take(500))) },
                            label = { Text("Anotação") }, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp))
                    }
                }
                itemsIndexed(draft.entries, key = { index, entry -> if (uniqueIds) entry.exercise.id else index }) { index, entry ->
                    val exercise = entry.exercise
                    val previous = remember(sessions, exercise.key(), exercise.unit, exercise.cardio, draft.id, draft.date) {
                        sessions.previous(exercise, draft)
                    }
                    ExerciseCard(entry, previous, live, model.restFor(exercise), index > 0, index < draft.entries.lastIndex,
                        onChange = { change(draft.copy(entries = draft.entries.replacing(index, it))) },
                        onDone = { model.startRest(model.restFor(exercise), exercise.name) },
                        onRemove = { change(draft.copy(entries = draft.entries.removing(index))) },
                        onMove = { change(draft.copy(entries = draft.entries.moving(index, index + it))) },
                        onInfo = { info = exercise },
                        onRest = { restFor = index },
                        onVariant = { variantFor = index })
                }
                item(key = "adicionar") {
                    FilledTonalButton(onClick = { focus.clearFocus(); picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar exercício")
                    }
                }
                item(key = "descartar") {
                    TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (live) "Encerrar sem salvar" else "Fechar sem salvar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (picking) {
            ExercisePicker(model, custom,
                onAdd = { added -> change(draft.copy(entries = draft.entries + added.map { blankEntry(it.copy(id = newId())) })) },
                onDismiss = { picking = false })
        }
    }

    if (confirm) {
        Confirm(if (live) "Encerrar sem salvar?" else "Fechar sem salvar?",
            if (live) "O treino é encerrado e nada dele vai para o histórico." else "As alterações deste registro serão descartadas.",
            confirm = if (live) "Encerrar" else "Fechar",
            onConfirm = {
                confirm = false
                if (live) model.discardWorkout() else model.closeRecord()
            }, onDismiss = { confirm = false }, dismiss = "Continuar")
    }
    info?.let { exercise ->
        val stats = remember(sessions, exercise.key()) {
            sessions.exerciseStats().firstOrNull { it.cardio == exercise.cardio && it.key.endsWith(":" + exercise.key()) }
        }
        ExerciseSheet(exercise, model.catalog, stats, onDismiss = { info = null })
    }
    variantFor?.let { index ->
        val entry = draft.entries.getOrNull(index)
        if (entry == null) variantFor = null else VariantDialog(entry.exercise, onConfirm = { text ->
            change(draft.copy(entries = draft.entries.replacing(index,
                entry.copy(exercise = entry.exercise.withVariant(text, model.catalog)))))
        }, onDismiss = { variantFor = null })
    }
    if (finishing && live) {
        FinishSheet(model, onDiscard = { finishing = false; confirm = true }, onDismiss = { finishing = false })
    }
    restFor?.let { index ->
        val entry = draft.entries.getOrNull(index)
        if (entry == null) restFor = null else RestPicker(
            title = "Descanso em ${entry.exercise.name}", initial = model.restFor(entry.exercise), confirm = "Usar neste exercício",
            onConfirm = { seconds ->
                change(draft.copy(entries = draft.entries.replacing(index, entry.copy(exercise = entry.exercise.copy(rest = seconds)))))
            },
            onDismiss = { restFor = null })
    }
}

private fun newId() = java.util.UUID.randomUUID().toString()

/** Exercícios criados à mão que já apareceram no histórico, para reaproveitar no seletor. */
fun List<Session>.customExercises(): List<Exercise> = asSequence().flatMap { it.entries.asSequence() }
    .map { it.exercise }.filter { it.ref.isEmpty() && it.name.isNotBlank() }
    .distinctBy { it.key() + it.cardio }.take(80).toList()

@Composable
private fun Elapsed(start: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Text(clock(((now - start) / 1000).coerceAtLeast(0)),
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ExerciseCard(
    entry: Entry, previous: Entry?, live: Boolean, rest: Int, canMoveUp: Boolean, canMoveDown: Boolean,
    onChange: (Entry) -> Unit, onDone: () -> Unit, onRemove: () -> Unit, onMove: (Int) -> Unit,
    onInfo: () -> Unit, onRest: () -> Unit, onVariant: () -> Unit
) {
    val exercise = entry.exercise
    val colors = MaterialTheme.colorScheme
    val hint = remember(exercise, previous) { suggestion(exercise, previous) }
    var menu by remember { mutableStateOf(false) }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExercisePhoto(exercise.ref, exercise.name, Modifier.size(width = 64.dp, height = 46.dp).clickable(onClick = onInfo))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(exercise.name.ifBlank { "Exercício" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(previous?.let { "Último: ${it.summary()}" } ?: "Primeiro registro",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções de ${exercise.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Como fazer") }, onClick = { menu = false; onInfo() })
                    if (!exercise.cardio) DropdownMenuItem(text = { Text("Descanso: ${clock(rest)}") },
                        onClick = { menu = false; onRest() })
                    if (!exercise.cardio) DropdownMenuItem(
                        text = { Text(if (exercise.variant.isBlank()) "Variação" else "Variação: ${exercise.variant}") },
                        onClick = { menu = false; onVariant() })
                    if (canMoveUp) DropdownMenuItem(text = { Text("Mover para cima") }, onClick = { menu = false; onMove(-1) })
                    if (canMoveDown) DropdownMenuItem(text = { Text("Mover para baixo") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remover exercício", color = colors.error) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }

        if (hint?.increase == true) {
            Surface(color = colors.tertiaryContainer, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp), tint = colors.onTertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Meta batida no último treino. Suba para ${hint.load} ${exercise.unit}.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onTertiaryContainer)
                }
            }
        }
        if (exercise.note.isNotBlank()) {
            Text(exercise.note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(if (exercise.cardio) "Nº" else "Série", Modifier.width(40.dp))
            Label(if (exercise.cardio) "Minutos" else "Carga (${exercise.unit})", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Label(if (exercise.cardio) "Distância (km)" else "Repetições", Modifier.weight(1f))
            if (live) Spacer(Modifier.width(54.dp))
        }
        entry.sets.forEachIndexed { row, set ->
            val before = previous?.sets?.let { it.getOrNull(row) ?: it.lastOrNull() }
            val firstHint = if (exercise.cardio) before?.first.orEmpty() else hint?.load ?: before?.first.orEmpty()
            val secondHint = when {
                exercise.cardio -> before?.second.orEmpty()
                hint?.increase == true -> hint.reps
                else -> before?.second ?: hint?.reps.orEmpty()
            }
            val update = { changed: SetEntry -> onChange(entry.copy(sets = entry.sets.replacing(row, changed))) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.size(30.dp).clip(CircleShape)
                        .background(if (set.done) colors.primary else colors.surfaceContainerHighest),
                        contentAlignment = Alignment.Center) {
                        Text("${row + 1}", style = MaterialTheme.typography.labelLarge,
                            color = if (set.done) colors.onPrimary else colors.onSurfaceVariant)
                    }
                }
                NumberBox(set.first, { update(set.copy(first = it)) }, firstHint, Modifier.weight(1f), decimal = true,
                    highlighted = set.done)
                Spacer(Modifier.width(8.dp))
                NumberBox(set.second, { update(set.copy(second = it)) }, secondHint, Modifier.weight(1f),
                    decimal = exercise.cardio, highlighted = set.done)
                if (live) {
                    Spacer(Modifier.width(8.dp))
                    Surface(onClick = {
                        if (set.done) update(set.copy(done = false)) else {
                            // concluir sem digitar aceita os valores sugeridos
                            val filled = set.copy(first = set.first.ifBlank { firstHint },
                                second = set.second.ifBlank { secondHint },
                                speed = set.speed.ifBlank { if (exercise.cardio) before?.speed.orEmpty() else "" }, done = true)
                            if (!filled.empty() && filled.valid(exercise.cardio)) {
                                update(filled)
                                if (!exercise.cardio) onDone()
                            }
                        }
                    }, shape = RoundedCornerShape(12.dp), color = if (set.done) colors.primary else colors.surfaceContainerHighest,
                        modifier = Modifier.size(46.dp).semantics {
                            contentDescription = if (set.done) "Desfazer série ${row + 1}" else "Concluir série ${row + 1}"
                        }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, tint = if (set.done) colors.onPrimary else colors.onSurfaceVariant)
                        }
                    }
                }
            }
            if (exercise.cardio) {
                // velocidade (ou nível do aparelho) e calorias são opcionais
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Spacer(Modifier.width(40.dp))
                    Label("Velocidade ou nível", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Label("Calorias (kcal)", Modifier.weight(1f))
                    if (live) Spacer(Modifier.width(54.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(40.dp))
                    NumberBox(set.speed, { update(set.copy(speed = it)) }, before?.speed.orEmpty(), Modifier.weight(1f),
                        decimal = true, highlighted = set.done)
                    Spacer(Modifier.width(8.dp))
                    NumberBox(set.calories, { update(set.copy(calories = it)) }, "", Modifier.weight(1f), decimal = true,
                        highlighted = set.done)
                    if (live) Spacer(Modifier.width(54.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(entry.copy(sets = entry.sets + SetEntry())) }) {
                Text(if (exercise.cardio) "+ Atividade" else "+ Série")
            }
            if (entry.sets.size > 1) TextButton(onClick = { onChange(entry.copy(sets = entry.sets.dropLast(1))) }) {
                Text(if (exercise.cardio) "− Atividade" else "− Série", color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (!exercise.cardio) TextButton(onClick = onRest) {
                Icon(Icons.Filled.Timer, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(clock(rest))
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, maxLines = 1)
}

val RATING_LABELS = listOf("Sem avaliação", "Péssimo", "Fraco", "Ok", "Bom", "Excelente")

/** Cinco estrelas. Com [onChange] nulo, só mostra a nota. */
@Composable
fun RatingStars(rating: Int, onChange: ((Int) -> Unit)?, modifier: Modifier = Modifier, size: Int = 40) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..5) {
            val tint = if (star <= rating) colors.tertiary else colors.outlineVariant
            if (onChange == null) Icon(Icons.Filled.Star, null, Modifier.size(size.dp), tint = tint)
            else IconButton(onClick = { onChange(star) }, modifier = Modifier.size((size + 8).dp)) {
                Icon(Icons.Filled.Star, "$star de 5: ${RATING_LABELS[star]}", Modifier.size(size.dp), tint = tint)
            }
        }
    }
}

/** Folha de conclusão: resumo do que será salvo, avaliação do treino e a opção de encerrar sem salvar. */
@Composable
private fun FinishSheet(model: TrainingModel, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val preview = remember { model.finishPreview() } ?: return
    var rating by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val volume = remember(preview) { preview.entries.fold(java.math.BigDecimal.ZERO) { sum, entry -> sum + entry.volume() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Concluir treino", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(
                durationLabel(preview.seconds).ifEmpty { "0 s" },
                if (preview.sets == 1) "1 série" else "${preview.sets} séries",
                volume.takeIf { it.signum() > 0 }?.let { "${it.pretty()} kg no total" }
            ).joinToString(" · "), color = colors.onSurfaceVariant)
            if (preview.sets == 0) {
                Text("Nenhuma série foi preenchida. O treino fica salvo só com a data, a duração e a sua avaliação.",
                    color = colors.onSurfaceVariant)
            }
            if (preview.dropped > 0) {
                Text((if (preview.dropped == 1) "1 série incompleta fica" else "${preview.dropped} séries incompletas ficam") +
                    " de fora do registro. Para incluir, volte e preencha a carga e as repetições.", color = colors.tertiary)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Como foi o treino?", style = MaterialTheme.typography.titleMedium)
                RatingStars(rating, onChange = { rating = if (rating == it) 0 else it }, modifier = Modifier.padding(top = 4.dp), size = 44)
                Text(RATING_LABELS[rating], color = if (rating == 0) colors.onSurfaceVariant else colors.tertiary,
                    style = MaterialTheme.typography.labelLarge)
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("Anotação (opcional)") },
                placeholder = { Text("Ex.: dormi mal, ombro incomodou") }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp))
            Button(onClick = { onDismiss(); model.finishWorkout(rating, note) }, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Salvar treino") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("Voltar ao treino") }
                TextButton(onClick = onDiscard) { Text("Encerrar sem salvar", color = colors.error) }
            }
        }
    }
}
