package br.com.meutreino

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var model: TrainingModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // barras do sistema transparentes com ícones claros, porque o app é sempre escuro
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[TrainingModel::class.java]
        if (savedInstanceState == null) receive(intent)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TrainingApp(model) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.visible = true
        RestAlarm.clearDone(this)
    }

    override fun onStop() {
        AppState.visible = false
        super.onStop()
    }

    /** Arquivo ou texto enviado por "Abrir com" ou "Compartilhar" de outro app. */
    private fun receive(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" }?.let(model::importFile)
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                when {
                    stream != null && stream.scheme == "content" -> model.importFile(stream)
                    else -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { model.importText(it, "Texto compartilhado") }
                }
            }
        }
    }
}

private val planTypes = arrayOf("text/*", "application/pdf", "application/octet-stream")
private val backupTypes = arrayOf("application/json", "text/*", "application/octet-stream")

@Composable
private fun TrainingApp(model: TrainingModel) {
    val routines by model.routines.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pickPlan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importFile) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importBackup) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(model::exportBackup)
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // o descanso continua contando em qualquer tela
    LaunchedEffect(model.timer.running) {
        while (model.timer.running) {
            model.tickRest()
            delay(200)
        }
    }
    // a notificação de fim de descanso precisa de permissão a partir do Android 13
    LaunchedEffect(model.activeWorkout != null) {
        if (model.activeWorkout != null && Build.VERSION.SDK_INT >= 33 && !model.askedNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            model.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(model.message) {
        if (model.message.isNotEmpty()) {
            val text = model.message
            model.message = ""
            snackbar.showSnackbar(text)
        }
    }

    val importing = model.importState
    val routine = model.routineDraft
    val record = model.recordDraft
    val active = model.activeWorkout

    Box(Modifier.fillMaxSize()) {
        when {
            importing != null -> ImportScreen(model, importing, onPickFile = { pickPlan.launch(planTypes) })
            routine != null -> RoutineScreen(model, routine, sessions)
            record != null -> WorkoutScreen(model, record, live = false, sessions = sessions)
            active != null && model.workoutOpen -> WorkoutScreen(model, active, live = true, sessions = sessions)
            else -> Tabs(model, routines, sessions, active,
                onPickPlan = { pickPlan.launch(planTypes) },
                onExport = { saveBackup.launch("meu-treino-backup-${LocalDate.now()}.json") },
                onRestore = { pickBackup.launch(backupTypes) })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun Tabs(
    model: TrainingModel, routines: List<RoutineItem>, sessions: List<Session>, active: WorkoutDraft?,
    onPickPlan: () -> Unit, onExport: () -> Unit, onRestore: () -> Unit
) {
    var info by remember { mutableStateOf<Exercise?>(null) }
    BackHandler(model.tab != Tab.Train) { model.tab = Tab.Train }
    Scaffold(
        bottomBar = {
            Column {
                RestBar(model)
                if (active != null) ActiveBanner(active) { model.workoutOpen = true }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(selected = model.tab == Tab.Train, onClick = { model.tab = Tab.Train },
                        icon = { Icon(Icons.Filled.FitnessCenter, null) }, label = { Text("Treinar") })
                    NavigationBarItem(selected = model.tab == Tab.History, onClick = { model.tab = Tab.History },
                        icon = { Icon(Icons.Filled.History, null) }, label = { Text("Histórico") })
                    NavigationBarItem(selected = model.tab == Tab.Library, onClick = { model.tab = Tab.Library },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }, label = { Text("Exercícios") })
                }
            }
        }
    ) { padding ->
        when (model.tab) {
            Tab.Train -> HomeScreen(model, routines, sessions, onPickPlan, onExport, onRestore, Modifier.padding(padding))
            Tab.History -> HistoryScreen(model, sessions, Modifier.padding(padding))
            Tab.Library -> Column(Modifier.padding(padding).fillMaxSize()) {
                Text("Exercícios", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp))
                val custom = remember(sessions) { sessions.customExercises() }
                ExerciseList(model.catalog, custom, selected = null, onToggle = {}, onInfo = { info = it },
                    modifier = Modifier.weight(1f))
            }
        }
    }
    info?.let { exercise ->
        val stats = remember(sessions, exercise.key()) {
            sessions.exerciseStats().firstOrNull { it.cardio == exercise.cardio && it.key.endsWith(":" + exercise.key()) }
        }
        ExerciseSheet(exercise, model.catalog, stats, onDismiss = { info = null })
    }
}

/** Faixa que leva de volta ao treino em andamento enquanto o usuário consulta outras abas. */
@Composable
private fun ActiveBanner(draft: WorkoutDraft, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(draft.date) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val done = draft.entries.sumOf { entry -> entry.sets.count { it.done } }
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Treino em andamento · ${clock(((now - draft.date) / 1000).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                Text("${draft.name} · ${if (done == 1) "1 série concluída" else "$done séries concluídas"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
            Text("Abrir", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
