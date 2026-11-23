package br.com.meutreino

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val PLAN_EXAMPLE = """# Treino A - Peito e tríceps
- Supino reto 4x8-12 40kg descanso 90s
- Crucifixo inclinado 3x12
- Tríceps corda 3x15 descanso 60s

# Treino B - Costas e bíceps
- Puxada frontal 4x10
- Remada baixa 3x12
- Rosca direta 3x10 20kg
- Esteira 15 min"""

/** Prévia do que foi lido do arquivo: o usuário confere, ajusta e salva as rotinas. */
@Composable
fun ImportScreen(model: TrainingModel, state: ImportState, onPickFile: () -> Unit) {
    val change: (ImportState) -> Unit = { model.importState = it }
    var pasting by remember { mutableStateOf(false) }
    var showIgnored by remember { mutableStateOf(false) }
    val count = state.routines.indices.count { it !in state.skipped && state.routines[it].exercises.isNotEmpty() }
    BackHandler { model.importState = null }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Importar treino")
                        Text(state.source, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { model.importState = null }) { Icon(Icons.Filled.Close, "Fechar") } })
        },
        bottomBar = {
            if (state.routines.isNotEmpty()) {
                Button(onClick = model::saveImport, enabled = count > 0 && !model.busy,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(when (count) {
                        0 -> "Marque ao menos uma rotina"
                        1 -> "Salvar 1 rotina"
                        else -> "Salvar $count rotinas"
                    })
                }
            }
        }
    ) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Lendo o treino…", Modifier.padding(top = 16.dp))
            }
            state.routines.isEmpty() -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(state.error.ifEmpty { "Não encontrei exercícios nesse conteúdo." },
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
                item {
                    Text("O app entende arquivos .md, .txt e .pdf com texto. Cada treino começa com um título e cada " +
                        "exercício fica em uma linha com séries × repetições. Carga e descanso são opcionais. Por exemplo:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
                        Text(PLAN_EXAMPLE, Modifier.fillMaxWidth().padding(14.dp), fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text("Escolher outro arquivo") } }
                item { OutlinedButton(onClick = { pasting = true }, modifier = Modifier.fillMaxWidth()) { Text("Colar o texto do treino") } }
                item {
                    TextButton(onClick = { model.importText(PLAN_EXAMPLE, "Exemplo") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver como fica com o exemplo")
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val exercises = state.routines.sumOf { it.exercises.size }
                    Text("Encontrei ${state.routines.size} ${if (state.routines.size == 1) "rotina" else "rotinas"} com " +
                        "$exercises ${if (exercises == 1) "exercício" else "exercícios"}. Confira antes de salvar. " +
                        "Depois dá para ajustar tudo em Editar rotina.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(state.routines) { index, routine ->
                    val included = index !in state.skipped
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = included, onCheckedChange = {
                                change(state.copy(skipped = if (included) state.skipped + index else state.skipped - index))
                            })
                            OutlinedTextField(value = routine.name, singleLine = true, modifier = Modifier.weight(1f),
                                label = { Text("Nome da rotina") }, shape = RoundedCornerShape(14.dp),
                                onValueChange = { name ->
                                    change(state.copy(routines = state.routines.replacing(index, routine.copy(name = name.take(60)))))
                                })
                        }
                        routine.exercises.forEachIndexed { position, exercise ->
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ExercisePhoto(exercise.ref, exercise.name, Modifier.size(width = 62.dp, height = 44.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(exercise.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(exercise.plan(),
                                        exercise.load.takeIf { it.isNotBlank() }?.let { "$it ${exercise.unit}" },
                                        exercise.note.takeIf { it.isNotBlank() && !exercise.cardio },
                                        "sem foto na biblioteca".takeIf { exercise.ref.isEmpty() }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    change(state.copy(routines = state.routines.replacing(index,
                                        routine.copy(exercises = routine.exercises.removing(position)))))
                                }) { Icon(Icons.Filled.Close, "Remover ${exercise.name}") }
                            }
                        }
                    }
                }
                if (state.ignored.isNotEmpty()) item {
                    TextButton(onClick = { showIgnored = !showIgnored }) {
                        Text(if (showIgnored) "Ocultar linhas não reconhecidas"
                            else "Ver ${state.ignored.size} ${if (state.ignored.size == 1) "linha não reconhecida" else "linhas não reconhecidas"}")
                    }
                    if (showIgnored) Text(state.ignored.joinToString("\n"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (pasting) PasteDialog(onImport = { pasting = false; model.importText(it) }, onDismiss = { pasting = false })
}

@Composable
fun PasteDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Colar treino") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.take(100_000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
                placeholder = { Text("Cole aqui o treino em texto ou Markdown") })
        },
        confirmButton = { TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
