package br.com.meutreino

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[TrainingModel::class.java]
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFA8F5BC),
                onPrimary = Color(0xFF103420),
                background = Color(0xFF101916),
                surface = Color(0xFF17231E)
            )) { TrainingApp(model) }
        }
    }
}

data class RoutineDraft(val id: Long = 0, val name: String = "", val exercises: List<Exercise> = emptyList())
data class WorkoutDraft(
    val id: Long = 0,
    val routineId: Long? = null,
    val name: String = "",
    val date: Long = System.currentTimeMillis(),
    val entries: List<Entry> = emptyList()
)

class TrainingModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(application, TrainingDatabase::class.java, "training.db").build()
    private val dao = database.trainingDao()
    val routines = dao.routines()
    val workouts = dao.workouts()
    var routineDraft by mutableStateOf<RoutineDraft?>(null)
    var workoutDraft by mutableStateOf<WorkoutDraft?>(null)
    var history by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    fun closeEditor() {
        if (busy) return
        routineDraft = null
        workoutDraft = null
        error = ""
    }

    fun editRoutine(routine: Routine? = null) {
        routineDraft = routine?.let { RoutineDraft(it.id, it.name, it.exercises.decodeExercises()) }
            ?: RoutineDraft()
    }

    fun start(routine: Routine? = null) {
        workoutDraft = routine?.let {
            WorkoutDraft(routineId = it.id, name = it.name, entries = it.exercises.decodeExercises().map(::blankEntry))
        } ?: WorkoutDraft()
    }

    fun edit(workout: Workout) {
        workoutDraft = WorkoutDraft(workout.id, workout.routineId, workout.name, workout.date,
            workout.entries.decodeEntries())
    }

    fun save(draft: RoutineDraft) {
        if (draft.name.isBlank() || draft.exercises.isEmpty() || draft.exercises.any {
            it.name.isBlank() || (!it.cardio && (it.unit.isBlank() || (it.sets.toIntOrNull() ?: 0) !in 1..100 ||
                (it.target.isNotBlank() && (it.target.toIntOrNull() ?: 0) < 1)))
        }) {
            error = "Preencha o nome, os exercícios e a quantidade de séries (1 a 100)."
            return
        }
        persist { dao.save(Routine(draft.id, draft.name.trim(), draft.exercises.map {
            it.copy(name = it.name.trim(), unit = it.unit.trim())
        }.encodeExercises())) }
    }

    fun save(draft: WorkoutDraft) {
        val entries = draft.entries.map { entry ->
            entry.copy(exercise = entry.exercise.copy(name = entry.exercise.name.trim(), unit = entry.exercise.unit.trim()),
                sets = entry.sets.filterNot(SetEntry::empty))
        }.filter { it.sets.isNotEmpty() }
        if (draft.name.isBlank() || entries.isEmpty() || entries.any { entry ->
            entry.exercise.name.isBlank() || (!entry.exercise.cardio &&
                (entry.exercise.unit.isBlank() || (entry.exercise.target.isNotBlank() &&
                    (entry.exercise.target.toIntOrNull() ?: 0) < 1))) ||
                entry.sets.any { !it.valid(entry.exercise.cardio) }
        }) {
            error = "Informe o nome do treino e complete os registros. Use duração em minutos ou carga e repetições."
            return
        }
        persist {
            dao.save(Workout(draft.id, draft.routineId, draft.name.trim(), draft.date, entries.encodeEntries()))
            history = true
        }
    }

    private fun persist(write: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        viewModelScope.launch {
            try {
                write()
                busy = false
                closeEditor()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Não foi possível salvar. Tente novamente."
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        database.close()
    }
}

private fun blankEntry(exercise: Exercise) = Entry(exercise,
    List(if (exercise.cardio) 1 else exercise.sets.toIntOrNull()?.coerceIn(1, 100) ?: 1) { SetEntry() })

private fun List<Workout>.previous(exercise: Exercise, draft: WorkoutDraft): Entry? =
    asSequence().filter { it.routineId == draft.routineId && it.id != draft.id && (draft.id == 0L ||
        it.date < draft.date || (it.date == draft.date && it.id < draft.id)) }
        .flatMap { it.entries.decodeEntries().asSequence() }
        .firstOrNull {
            val saved = it.exercise
            saved.cardio == exercise.cardio && saved.unit.equals(exercise.unit.trim(), true) &&
                (saved.id == exercise.id || saved.name.equals(exercise.name.trim(), true))
        }

@Composable
private fun TrainingApp(model: TrainingModel) {
    val routines by model.routines.collectAsStateWithLifecycle(initialValue = emptyList())
    val workouts by model.workouts.collectAsStateWithLifecycle(initialValue = emptyList())
    val routine = model.routineDraft
    val workout = model.workoutDraft
    val editing = routine != null || workout != null
    val focus = LocalFocusManager.current
    var confirmExit by remember { mutableStateOf(false) }
    val requestExit = { focus.clearFocus(); confirmExit = true }
    BackHandler(editing && !model.busy, onBack = requestExit)
    if (confirmExit) {
        AlertDialog(onDismissRequest = { confirmExit = false },
            title = { Text("Sair da edição?") },
            text = { Text("As alterações pendentes serão descartadas.") },
            confirmButton = { TextButton(onClick = {
                confirmExit = false
                model.closeEditor()
            }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar editando") } })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!editing) {
                NavigationBar {
                    NavigationBarItem(selected = !model.history, onClick = { model.history = false },
                        icon = { Text("▤") }, label = { Text("Rotinas") })
                    NavigationBarItem(selected = model.history, onClick = { model.history = true },
                        icon = { Text("◷") }, label = { Text("Histórico") })
                }
            }
        }
    ) { insets ->
        key(routine?.id, workout?.id, model.history) {
            Column(Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    routine != null && routine.id == 0L -> "Nova rotina"
                    routine != null -> "Editar rotina"
                    workout != null && workout.id == 0L -> "Registrar treino"
                    workout != null -> "Editar registro"
                    else -> "Meu Treino"
                }, style = MaterialTheme.typography.headlineLarge)
                when {
                    routine != null -> key("routine") {
                        RoutineEditor(routine) { model.routineDraft = it }
                    }
                    workout != null -> key("workout") {
                        WorkoutEditor(workout, workouts) { model.workoutDraft = it }
                    }
                    model.history -> History(workouts, model::edit)
                    else -> Routines(routines, model)
                }
                if (editing) {
                    if (model.error.isNotEmpty()) Text(model.error, color = MaterialTheme.colorScheme.error)
                    Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        focus.clearFocus()
                        if (routine != null) model.save(routine)
                        if (workout != null) model.save(workout)
                    }) { Text(if (model.busy) "Salvando…" else "Salvar") }
                    OutlinedButton(enabled = !model.busy, modifier = Modifier.fillMaxWidth(),
                        onClick = requestExit) { Text("Cancelar") }
                }
            }
        }
    }
}

@Composable
private fun Routines(routines: List<Routine>, model: TrainingModel) {
    Button(onClick = { model.start() }, modifier = Modifier.fillMaxWidth()) { Text("Registrar treino avulso") }
    OutlinedButton(onClick = { model.editRoutine() }, modifier = Modifier.fillMaxWidth()) { Text("Criar rotina") }
    if (routines.isEmpty()) Text("Crie uma rotina para reutilizar seus exercícios.")
    routines.forEach { routine ->
        Panel {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            Text(routine.exercises.decodeExercises().joinToString(" · ") { it.name })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.start(routine) }) { Text("Iniciar") }
                TextButton(onClick = { model.editRoutine(routine) }) { Text("Editar") }
            }
        }
    }
}

@Composable
private fun History(workouts: List<Workout>, edit: (Workout) -> Unit) {
    if (workouts.isEmpty()) Text("Seus treinos salvos aparecem aqui.")
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("pt", "BR")) }
    workouts.forEach { workout ->
        key(workout.id) {
            Panel {
                Text(workout.name, style = MaterialTheme.typography.titleLarge)
                Text(dateFormat.format(Date(workout.date)))
                workout.entries.decodeEntries().forEach { entry ->
                    Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(entry.summary())
                    if (entry.readyToIncrease()) Text("Meta atingida. Você pode avaliar subir a carga.",
                        color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { edit(workout) }) { Text("Editar registro") }
            }
        }
    }
}

@Composable
private fun RoutineEditor(draft: RoutineDraft, change: (RoutineDraft) -> Unit) {
    Field("Nome da rotina", draft.name, { change(draft.copy(name = it)) })
    draft.exercises.forEachIndexed { index, exercise ->
        key(exercise.id) {
            Panel {
                ExerciseFields(exercise, showPlan = true) { edited ->
                    change(draft.copy(exercises = draft.exercises.replacing(index, edited)))
                }
                TextButton(onClick = { change(draft.copy(exercises = draft.exercises.removing(index))) }) {
                    Text("Remover exercício")
                }
            }
        }
    }
    AddExercise { exercise -> change(draft.copy(exercises = draft.exercises + exercise)) }
}

@Composable
private fun WorkoutEditor(draft: WorkoutDraft, workouts: List<Workout>, change: (WorkoutDraft) -> Unit) {
    Field("Nome do treino", draft.name, { change(draft.copy(name = it)) })
    draft.entries.forEachIndexed { index, entry ->
        key(entry.exercise.id) {
            Panel {
                val exercise = entry.exercise
                val update = { edited: Entry -> change(draft.copy(entries = draft.entries.replacing(index, edited))) }
                ExerciseFields(exercise, showPlan = false) { update(entry.copy(exercise = it)) }
                val previous = remember(workouts, exercise, draft.id, draft.date) { workouts.previous(exercise, draft) }
                if (previous != null) {
                    Text("Último treino · " + previous.summary())
                    if (previous.readyToIncrease()) Text("Meta anterior atingida. Você pode avaliar subir a carga.",
                        color = MaterialTheme.colorScheme.primary)
                }
                entry.sets.forEachIndexed { row, values ->
                    Text(if (exercise.cardio) "Atividade ${row + 1}" else "Série ${row + 1}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(if (exercise.cardio) "Minutos" else "Carga (${exercise.unit})", values.first,
                            { update(entry.copy(sets = entry.sets.replacing(row, values.copy(first = it)))) },
                            Modifier.weight(1f), KeyboardType.Decimal)
                        Field(if (exercise.cardio) "Distância (km)" else "Repetições", values.second,
                            { update(entry.copy(sets = entry.sets.replacing(row, values.copy(second = it)))) },
                            Modifier.weight(1f), if (exercise.cardio) KeyboardType.Decimal else KeyboardType.Number)
                    }
                    if (entry.sets.size > 1) TextButton(onClick = {
                        update(entry.copy(sets = entry.sets.removing(row)))
                    }) { Text("Remover ${if (exercise.cardio) "atividade" else "série"} ${row + 1}") }
                }
                TextButton(onClick = { update(entry.copy(sets = entry.sets + SetEntry())) }) {
                    Text(if (exercise.cardio) "+ Atividade" else "+ Série")
                }
                TextButton(onClick = { change(draft.copy(entries = draft.entries.removing(index))) }) {
                    Text("Remover exercício")
                }
            }
        }
    }
    AddExercise { change(draft.copy(entries = draft.entries + blankEntry(it))) }
}

@Composable
private fun ExerciseFields(exercise: Exercise, showPlan: Boolean, change: (Exercise) -> Unit) {
    Field(if (exercise.cardio) "Nome da atividade" else "Nome do exercício", exercise.name,
        { change(exercise.copy(name = it)) })
    if (!exercise.cardio) {
        Field("Unidade de carga", exercise.unit, { change(exercise.copy(unit = it)) })
        if (showPlan) Field("Séries planejadas", exercise.sets, { change(exercise.copy(sets = it)) },
            keyboard = KeyboardType.Number)
        Field("Meta de repetições por série (opcional)", exercise.target, { change(exercise.copy(target = it)) },
            keyboard = KeyboardType.Number)
    }
}

@Composable
private fun AddExercise(add: (Exercise) -> Unit) {
    OutlinedButton(onClick = { add(Exercise()) }, modifier = Modifier.fillMaxWidth()) { Text("+ Exercício de força") }
    OutlinedButton(onClick = { add(Exercise(cardio = true, unit = "km", sets = "1")) },
        modifier = Modifier.fillMaxWidth()) { Text("+ Cardio") }
}

@Composable
private fun Field(label: String, value: String, update: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = update, modifier = modifier, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

private fun Entry.summary() = sets.joinToString(" · ") {
    if (exercise.cardio) {
        if (it.second.isBlank()) "${it.first} min" else "${it.first} min / ${it.second} km"
    } else "${it.first} ${exercise.unit} × ${it.second}"
}

private fun <T> List<T>.replacing(index: Int, value: T) = toMutableList().apply { set(index, value) }
private fun <T> List<T>.removing(index: Int) = toMutableList().apply { removeAt(index) }
