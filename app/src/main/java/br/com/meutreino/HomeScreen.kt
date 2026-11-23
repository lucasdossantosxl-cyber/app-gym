package br.com.meutreino

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Aba Treinar: começar um treino, rotinas salvas e importação de fichas. */
@Composable
fun HomeScreen(
    model: TrainingModel, routines: List<RoutineItem>, sessions: List<Session>,
    onPickPlan: () -> Unit, onExport: () -> Unit, onRestore: () -> Unit, modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var resting by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RoutineItem?>(null) }
    val lastUse = remember(sessions) {
        sessions.filter { it.workout.routineId != null }.groupBy { it.workout.routineId }
            .mapValues { (_, list) -> list.maxOf { it.workout.date } }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "titulo") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Meu Treino", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Seu treino, no seu ritmo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Descanso padrão: ${clock(model.defaultRest)}") },
                            onClick = { menu = false; resting = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Backup e atualização") }, onClick = { menu = false; settings = true })
                    }
                }
            }
        }
        item(key = "iniciar") {
            Button(onClick = { model.start() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Iniciar treino vazio", style = MaterialTheme.typography.titleMedium)
            }
        }
        item(key = "acoes") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { model.editRoutine() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nova rotina")
                }
                Box(Modifier.weight(1f)) {
                    FilledTonalButton(onClick = { importMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar")
                    }
                    DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                        DropdownMenuItem(text = { Text("Arquivo .md, .txt ou .pdf") }, onClick = { importMenu = false; onPickPlan() })
                        DropdownMenuItem(text = { Text("Colar texto") }, onClick = { importMenu = false; pasting = true })
                    }
                }
            }
        }
        item(key = "rotinas") {
            Text("Minhas rotinas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
        if (routines.isEmpty()) item(key = "vazio") {
            Panel {
                Text("Nenhuma rotina ainda", style = MaterialTheme.typography.titleMedium)
                Text("Monte uma rotina escolhendo exercícios da biblioteca, ou importe a ficha que você já tem em " +
                    "Markdown, texto ou PDF: o app lê os exercícios, as séries e as repetições e cria as rotinas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (routines.isEmpty() && sessions.isEmpty()) item(key = "restaurar") {
            Panel {
                Text("Já usava o Meu Treino?", style = MaterialTheme.typography.titleMedium)
                Text("Traga de volta as rotinas e o histórico de um arquivo de backup. A cópia automática fica em " +
                    "Downloads, na pasta MeuTreino.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                FilledTonalButton(onClick = onRestore, modifier = Modifier.padding(top = 10.dp)) { Text("Restaurar backup") }
            }
        }
        items(routines, key = { it.routine.id }) { item ->
            var options by remember { mutableStateOf(false) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.routine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(
                            if (item.exercises.size == 1) "1 exercício" else "${item.exercises.size} exercícios",
                            lastUse[item.routine.id]?.let { "último treino ${it.dayLabel()}" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { options = true }) { Icon(Icons.Filled.MoreVert, "Opções de ${item.routine.name}") }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { options = false; model.editRoutine(item) })
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { options = false; model.duplicate(item) })
                            DropdownMenuItem(text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                                onClick = { options = false; deleting = item })
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.exercises.take(4).forEach { exercise ->
                        ExercisePhoto(exercise.ref, exercise.name, Modifier.weight(1f).height(48.dp))
                    }
                    repeat(4 - item.exercises.take(4).size) { Spacer(Modifier.weight(1f)) }
                }
                Text(item.exercises.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { model.start(item) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(14.dp)) { Text("Iniciar") }
            }
        }
    }

    if (settings) BackupSheet(model, onExport = { settings = false; onExport() }, onRestore = { settings = false; onRestore() },
        onDismiss = { settings = false })
    if (pasting) PasteDialog(onImport = { pasting = false; model.importText(it) }, onDismiss = { pasting = false })
    if (resting) RestPicker("Descanso padrão", model.defaultRest, "Salvar", onConfirm = model::changeDefaultRest,
        onDismiss = { resting = false })
    deleting?.let { item ->
        Confirm("Excluir rotina?", "“${item.routine.name}” será apagada. Os treinos já registrados continuam no histórico.",
            confirm = "Excluir", onConfirm = { deleting = null; model.delete(item.routine) }, onDismiss = { deleting = null })
    }
}

private const val RELEASES = "https://github.com/lucasdossantosxl-cyber/app-gym/releases/latest"

/** Cópia automática, backup manual e atualização do app. */
@Composable
private fun BackupSheet(model: TrainingModel, onExport: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backup e atualização", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            if (AutoBackup.supported) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Cópia automática", style = MaterialTheme.typography.titleMedium)
                        Text(when {
                            !model.autoBackup -> "Desligada."
                            model.lastBackup == 0L -> "Ligada. A primeira cópia é feita quando você salvar uma rotina ou um treino."
                            else -> "Última cópia: ${model.lastBackup.dayLabel()} às ${model.lastBackup.timeLabel()}."
                        }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Switch(checked = model.autoBackup, onCheckedChange = model::changeAutoBackup)
                }
                Text("A cada alteração, o app grava ${AutoBackup.NAME} em Downloads, na pasta MeuTreino. O arquivo continua " +
                    "lá mesmo se o app for desinstalado.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text("O backup do Android (Configurações > Google > Backup), quando ligado, também guarda os dados do app na " +
                "sua conta Google e os devolve ao reinstalar ou trocar de celular.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Salvar cópia em…") }
                FilledTonalButton(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("Restaurar backup") }
            }

            HorizontalDivider()
            Text("Atualização", style = MaterialTheme.typography.titleMedium)
            Text("Você está na versão $version. As versões novas instalam por cima desta, sem apagar rotinas nem histórico.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            FilledTonalButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Ver a versão mais recente") }
        }
    }
}
