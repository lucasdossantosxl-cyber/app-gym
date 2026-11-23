package br.com.meutreino

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Cria ou edita uma rotina: nome, exercícios em ordem e o plano de cada um. */
@Composable
fun RoutineScreen(model: TrainingModel, draft: RoutineDraft, sessions: List<Session>) {
    val change: (RoutineDraft) -> Unit = { model.routineDraft = it }
    val focus = LocalFocusManager.current
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var info by remember { mutableStateOf<Exercise?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val custom = remember(sessions) { sessions.customExercises() }

    BackHandler(enabled = !picking) { focus.clearFocus(); confirmClose = true }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(title = { Text(if (draft.id == 0L) "Nova rotina" else "Editar rotina") },
                    navigationIcon = {
                        IconButton(onClick = { focus.clearFocus(); confirmClose = true }) { Icon(Icons.Filled.Close, "Fechar") }
                    },
                    actions = {
                        Button(enabled = !model.busy, modifier = Modifier.padding(end = 8.dp),
                            onClick = { focus.clearFocus(); model.saveRoutine() }) { Text("Salvar") }
                    })
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "nome") {
                    OutlinedTextField(value = draft.name, onValueChange = { change(draft.copy(name = it.take(60))) },
                        label = { Text("Nome da rotina") }, placeholder = { Text("Ex.: Treino A · Peito e tríceps") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                }
                if (model.error.isNotEmpty()) item(key = "erro") { Text(model.error, color = MaterialTheme.colorScheme.error) }
                if (draft.exercises.isEmpty()) item(key = "vazio") {
                    Text("Adicione os exercícios na ordem em que você treina. Toque em um exercício para ajustar séries, " +
                        "meta de repetições, descanso e incremento de carga.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(draft.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    var menu by remember { mutableStateOf(false) }
                    Surface(onClick = { focus.clearFocus(); editing = index }, shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExercisePhoto(exercise.ref, exercise.name, Modifier.size(width = 70.dp, height = 50.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(exercise.plan(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções de ${exercise.name}") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Ajustar plano") }, onClick = { menu = false; editing = index })
                                    DropdownMenuItem(text = { Text("Como fazer") }, onClick = { menu = false; info = exercise })
                                    if (index > 0) DropdownMenuItem(text = { Text("Mover para cima") }, onClick = {
                                        menu = false
                                        change(draft.copy(exercises = draft.exercises.moving(index, index - 1)))
                                    })
                                    if (index < draft.exercises.lastIndex) DropdownMenuItem(text = { Text("Mover para baixo") }, onClick = {
                                        menu = false
                                        change(draft.copy(exercises = draft.exercises.moving(index, index + 1)))
                                    })
                                    DropdownMenuItem(text = { Text("Remover", color = MaterialTheme.colorScheme.error) }, onClick = {
                                        menu = false
                                        change(draft.copy(exercises = draft.exercises.removing(index)))
                                    })
                                }
                            }
                        }
                    }
                }
                item(key = "adicionar") {
                    FilledTonalButton(onClick = { focus.clearFocus(); picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar exercícios")
                    }
                }
                if (draft.id != 0L) item(key = "excluir") {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Excluir rotina", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (picking) {
            ExercisePicker(model, custom,
                onAdd = { added -> change(draft.copy(exercises = draft.exercises + added.map { it.copy(id = java.util.UUID.randomUUID().toString()) })) },
                onDismiss = { picking = false })
        }
    }

    editing?.let { index ->
        val exercise = draft.exercises.getOrNull(index)
        if (exercise == null) editing = null else PlanSheet(exercise, model.defaultRest,
            onChange = { change(draft.copy(exercises = draft.exercises.replacing(index, it))) },
            onDismiss = { editing = null })
    }
    info?.let { ExerciseSheet(it, model.catalog, stats = null, onDismiss = { info = null }) }
    if (confirmClose) {
        Confirm("Sair da edição?", "As alterações desta rotina serão descartadas.", confirm = "Descartar",
            onConfirm = { confirmClose = false; model.closeRoutine() }, onDismiss = { confirmClose = false },
            dismiss = "Continuar editando")
    }
    if (confirmDelete) {
        Confirm("Excluir rotina?", "Os treinos já registrados com ela continuam no histórico.", confirm = "Excluir",
            onConfirm = {
                confirmDelete = false
                model.routines.value.firstOrNull { it.routine.id == draft.id }?.let { model.delete(it.routine) }
            }, onDismiss = { confirmDelete = false })
    }
}

private val restOptions = listOf(0, 30, 45, 60, 90, 120, 180, 240)

/** Plano de um exercício dentro da rotina. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSheet(exercise: Exercise, defaultRest: Int, onChange: (Exercise) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exercise.name.ifBlank { "Exercício" }, style = MaterialTheme.typography.titleLarge)
            if (exercise.ref.isEmpty()) Field("Nome", exercise.name, { onChange(exercise.copy(name = it.take(60))) })
            if (!exercise.cardio) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Séries", exercise.sets, { onChange(exercise.copy(sets = it.filter(Char::isDigit).take(3))) },
                        Modifier.weight(1f), KeyboardType.Number)
                    Field("Meta de repetições", exercise.target, { onChange(exercise.copy(target = it.filter(Char::isDigit).take(3))) },
                        Modifier.weight(1f), KeyboardType.Number)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Carga inicial (${exercise.unit})", exercise.load, { onChange(exercise.copy(load = it.take(7))) },
                        Modifier.weight(1f), KeyboardType.Decimal)
                    Field("Incremento (${exercise.unit})", exercise.increment, { onChange(exercise.copy(increment = it.take(6))) },
                        Modifier.weight(1f), KeyboardType.Decimal)
                }
                Text("Quando você bate a meta de repetições em todas as séries com a mesma carga, o app sugere somar o " +
                    "incremento no treino seguinte.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Unidade", Modifier.padding(end = 4.dp))
                    listOf("kg", "lb").forEach { unit ->
                        FilterChip(selected = exercise.unit.equals(unit, true), onClick = { onChange(exercise.copy(unit = unit)) },
                            label = { Text(unit) })
                    }
                }
                Text("Descanso entre séries")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    restOptions.forEach { seconds ->
                        FilterChip(selected = exercise.rest == seconds, onClick = { onChange(exercise.copy(rest = seconds)) },
                            label = { Text(if (seconds == 0) "Padrão (${clock(defaultRest)})" else clock(seconds)) })
                    }
                }
            }
            Field("Observação", exercise.note, { onChange(exercise.copy(note = it.take(120))) })
            Field("Link de vídeo (opcional)", exercise.video, { onChange(exercise.copy(video = it.take(300))) },
                keyboard = KeyboardType.Uri, supporting = "Sem link, o app busca vídeos de execução pelo nome.")
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Pronto") }
        }
    }
}
