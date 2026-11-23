package br.com.meutreino

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { Train, History, Library }

data class ImportState(
    val source: String,
    val loading: Boolean = false,
    val error: String = "",
    val routines: List<RoutineDraft> = emptyList(),
    val skipped: Set<Int> = emptySet(),
    val ignored: List<String> = emptyList()
)

class TrainingModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val database = Room.databaseBuilder(application, TrainingDatabase::class.java, "training.db")
        .addMigrations(TrainingDatabase.MIGRATION_1_2).build()
    private val dao = database.trainingDao()
    private val prefs = application.getSharedPreferences("meutreino", Context.MODE_PRIVATE)

    val catalog: Catalog = Catalog.parse(application.assets.open("exercises.json").bufferedReader().use { it.readText() })

    val routines: StateFlow<List<RoutineItem>> = dao.routines()
        .map { list -> list.map { RoutineItem(it, runCatching { it.exercises.decodeExercises() }.getOrDefault(emptyList())) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<Session>> = dao.workouts()
        .map { list -> list.map { Session(it, runCatching { it.entries.decodeEntries() }.getOrDefault(emptyList())) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var tab by mutableStateOf(Tab.Train)
    var routineDraft by mutableStateOf<RoutineDraft?>(null)
    /** Treino em andamento. Fica guardado mesmo se o app for fechado. */
    var activeWorkout by mutableStateOf<WorkoutDraft?>(restoreDraft())
    /** O treino em andamento está aberto em tela cheia (ou recolhido atrás das abas). */
    var workoutOpen by mutableStateOf(false)
    /** Registro do histórico em edição. */
    var recordDraft by mutableStateOf<WorkoutDraft?>(null)
    var importState by mutableStateOf<ImportState?>(null)
    var message by mutableStateOf("")
    var defaultRest by mutableIntStateOf(prefs.getInt("rest", 90).coerceIn(5, RestTimer.MAX_SECONDS))
        private set
    val timer = RestTimer()
    /** A permissão de notificação é pedida uma única vez, no primeiro treino. */
    var askedNotifications: Boolean
        get() = prefs.getBoolean("askedNotifications", false)
        set(value) = prefs.edit().putBoolean("askedNotifications", value).apply()
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")

    init {
        viewModelScope.launch {
            snapshotFlow { activeWorkout }.collectLatest { draft ->
                if (draft != null) {
                    delay(300)
                    val json = withContext(Dispatchers.Default) { draft.encodeDraft() }
                    prefs.edit().putString("draft", json).apply()
                }
            }
        }
    }

    private fun restoreDraft(): WorkoutDraft? =
        prefs.getString("draft", null)?.let { runCatching { it.decodeDraft() }.getOrNull() }

    private fun clearDraft() {
        activeWorkout = null
        workoutOpen = false
        prefs.edit().remove("draft").apply()
    }

    // ------------------------------------------------------------------ rotinas

    fun editRoutine(item: RoutineItem? = null) {
        error = ""
        routineDraft = item?.let { RoutineDraft(it.routine.id, it.routine.name, it.exercises) } ?: RoutineDraft()
    }

    fun closeRoutine() {
        if (busy) return
        routineDraft = null
        error = ""
    }

    fun saveRoutine() {
        val draft = routineDraft ?: return
        if (draft.name.isBlank()) return fail("Dê um nome para a rotina.")
        if (draft.exercises.isEmpty()) return fail("Adicione pelo menos um exercício.")
        if (draft.exercises.any { !it.validPlan() }) {
            return fail("Confira as séries (1 a 100), a meta de repetições e o incremento de carga.")
        }
        persist {
            dao.save(Routine(draft.id, draft.name.trim(), draft.exercises.map(::trimmed).encodeExercises()))
            routineDraft = null
        }
    }

    fun duplicate(item: RoutineItem) = persist {
        dao.save(Routine(0, item.routine.name + " (cópia)",
            item.exercises.map { it.copy(id = java.util.UUID.randomUUID().toString()) }.encodeExercises()))
    }

    fun delete(routine: Routine) = persist {
        dao.delete(routine)
        if (routineDraft?.id == routine.id) routineDraft = null
        message = "Rotina excluída. O histórico foi mantido."
    }

    // ------------------------------------------------------------------- treino

    fun start(item: RoutineItem? = null) {
        error = ""
        if (activeWorkout != null) {
            workoutOpen = true
            message = "Conclua ou descarte o treino em andamento antes de começar outro."
            return
        }
        activeWorkout = WorkoutDraft(routineId = item?.routine?.id, name = item?.routine?.name ?: "Treino avulso",
            entries = item?.exercises.orEmpty().map(::blankEntry))
        workoutOpen = true
    }

    fun discardWorkout() {
        clearDraft()
        stopRest()
        error = ""
    }

    fun finishWorkout() {
        val draft = activeWorkout ?: return
        val entries = cleaned(draft.entries) ?: return
        val seconds = ((System.currentTimeMillis() - draft.date) / 1000).coerceIn(0, 12 * 3600)
        persist {
            dao.save(Workout(0, draft.routineId, draft.name.trim().ifEmpty { "Treino" }, draft.date,
                entries.encodeEntries(), seconds))
            clearDraft()
            stopRest()
            tab = Tab.History
            message = "Treino salvo."
        }
    }

    fun edit(session: Session) {
        error = ""
        recordDraft = WorkoutDraft(session.workout.id, session.workout.routineId, session.workout.name,
            session.workout.date, session.entries, session.workout.duration)
    }

    fun closeRecord() {
        if (busy) return
        recordDraft = null
        error = ""
    }

    fun saveRecord() {
        val draft = recordDraft ?: return
        val entries = cleaned(draft.entries) ?: return
        if (draft.name.isBlank()) return fail("Dê um nome para o treino.")
        persist {
            dao.save(Workout(draft.id, draft.routineId, draft.name.trim(), draft.date, entries.encodeEntries(),
                draft.duration))
            recordDraft = null
        }
    }

    fun delete(workout: Workout) = persist {
        dao.delete(workout)
        if (recordDraft?.id == workout.id) recordDraft = null
        message = "Registro excluído."
    }

    /** Tira séries vazias e exercícios sem série. Devolve null (com mensagem de erro) se algo estiver inválido. */
    private fun cleaned(entries: List<Entry>): List<Entry>? {
        val kept = entries.map { entry ->
            entry.copy(exercise = trimmed(entry.exercise), sets = entry.sets.filterNot(SetEntry::empty))
        }.filter { it.sets.isNotEmpty() }
        if (kept.isEmpty()) {
            fail("Preencha pelo menos uma série antes de salvar.")
            return null
        }
        val broken = kept.firstOrNull { entry ->
            entry.exercise.name.isBlank() || entry.sets.any { !it.valid(entry.exercise.cardio) }
        }
        if (broken != null) {
            fail(if (broken.exercise.cardio) "Confira o tempo em ${broken.exercise.name.ifBlank { "cardio" }}."
                else "Confira carga e repetições em ${broken.exercise.name.ifBlank { "um exercício sem nome" }}.")
            return null
        }
        return kept
    }

    // ----------------------------------------------------------------- descanso

    fun restFor(exercise: Exercise) = exercise.rest.takeIf { it > 0 } ?: defaultRest

    fun changeDefaultRest(seconds: Int) {
        defaultRest = seconds.coerceIn(5, RestTimer.MAX_SECONDS)
        prefs.edit().putInt("rest", defaultRest).apply()
    }

    fun startRest(seconds: Int, label: String = "") {
        timer.start(seconds, label)
        RestAlarm.schedule(app, timer.remaining, label)
    }

    fun adjustRest(seconds: Int) {
        timer.add(seconds)
        syncRest()
    }

    fun toggleRest() {
        timer.toggle()
        syncRest()
    }

    fun stopRest() {
        timer.stop()
        RestAlarm.cancel(app)
    }

    /** Chamado várias vezes por segundo enquanto o descanso corre com o app aberto. */
    fun tickRest() {
        if (timer.tick() && AppState.visible) {
            RestAlarm.cancel(app)
            RestAlarm.signal(app)
        }
    }

    private fun syncRest() {
        when {
            timer.running -> RestAlarm.schedule(app, timer.remaining, timer.label)
            timer.finished -> {
                RestAlarm.cancel(app)
                RestAlarm.signal(app)
            }
            else -> RestAlarm.cancel(app)
        }
    }

    // --------------------------------------------------------------- importação

    fun importFile(uri: Uri) {
        importState = ImportState(source = "Lendo arquivo…", loading = true)
        viewModelScope.launch {
            importState = withContext(Dispatchers.IO) {
                val name = PlanFile.name(app, uri)
                try {
                    val text = PlanFile.read(app, uri)
                    if (Backup.looksLikeBackup(text)) ImportState(name, error = restoreBackup(text))
                    else parsed(name, text)
                } catch (error: PlanFileException) {
                    ImportState(name, error = error.message.orEmpty())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ImportState(name, error = "Não foi possível ler o arquivo.")
                }
            }
            // um backup restaurado não tem prévia para mostrar
            importState?.takeIf { it.error.startsWith("Backup") }?.let {
                message = it.error
                importState = null
            }
        }
    }

    fun importText(text: String, source: String = "Texto colado") {
        if (text.isBlank()) return
        importState = ImportState(source, loading = true)
        viewModelScope.launch {
            importState = withContext(Dispatchers.Default) { parsed(source, text.take(400_000)) }
        }
    }

    private fun parsed(source: String, text: String): ImportState {
        val plan = PlanParser.parse(text, catalog)
        return ImportState(source, routines = plan.routines, ignored = plan.ignored,
            error = if (plan.routines.isEmpty()) "Não encontrei exercícios nesse conteúdo." else "")
    }

    fun saveImport() {
        val state = importState ?: return
        val chosen = state.routines.filterIndexed { index, routine ->
            index !in state.skipped && routine.exercises.isNotEmpty()
        }
        if (chosen.isEmpty()) return
        persist {
            chosen.forEach { dao.save(Routine(0, it.name.trim().ifEmpty { "Treino importado" },
                it.exercises.map(::trimmed).encodeExercises())) }
            importState = null
            tab = Tab.Train
            message = if (chosen.size == 1) "Rotina importada." else "${chosen.size} rotinas importadas."
        }
    }

    // ------------------------------------------------------------------- backup

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    val json = Backup.encode(dao.allRoutines(), dao.allWorkouts())
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
                "Backup salvo."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "Não foi possível salvar o backup."
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            message = withContext(Dispatchers.IO) {
                try {
                    restoreBackup(PlanFile.read(app, uri))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "Não foi possível ler o arquivo."
                }
            }
        }
    }

    private suspend fun restoreBackup(text: String): String = try {
        val (routines, workouts) = Backup.restore(dao, Backup.decode(text))
        "Backup restaurado: $routines rotinas e $workouts treinos acrescentados."
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        "Esse arquivo não é um backup válido do Meu Treino."
    }

    // -------------------------------------------------------------------- apoio

    private fun fail(text: String) {
        error = text
    }

    private fun persist(write: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        viewModelScope.launch {
            try {
                write()
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

fun blankEntry(exercise: Exercise) = Entry(exercise,
    List(if (exercise.cardio) 1 else exercise.sets.toIntOrNull()?.coerceIn(1, 100) ?: 1) { SetEntry() })

private fun trimmed(exercise: Exercise) = exercise.copy(name = exercise.name.trim(),
    unit = exercise.unit.trim().ifEmpty { if (exercise.cardio) "km" else "kg" }, note = exercise.note.trim(),
    video = exercise.video.trim())

fun Exercise.validPlan(): Boolean = name.isNotBlank() && (cardio || (
    (sets.toIntOrNull() ?: 0) in 1..100 &&
        (target.isBlank() || (target.toIntOrNull() ?: 0) >= 1) &&
        (increment.isBlank() || increment.decimal()?.let { it.signum() > 0 } == true) &&
        (load.isBlank() || load.decimal() != null)))
