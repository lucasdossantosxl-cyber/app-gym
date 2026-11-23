package br.com.meutreino

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Cronômetro de descanso. Guarda o instante em que o descanso termina, em vez de contar segundos,
 * para continuar certo quando a tela apaga ou o app vai para segundo plano.
 */
class RestTimer(private val now: () -> Long = SystemClock::elapsedRealtime) {
    /** Tempo total do descanso atual, em milissegundos. */
    var duration by mutableLongStateOf(0L)
        private set
    var remaining by mutableLongStateOf(0L)
        private set
    private var endsAt by mutableLongStateOf(0L)
    var finished by mutableStateOf(false)
        private set
    var label by mutableStateOf("")
        private set

    val running get() = endsAt != 0L
    val paused get() = !running && !finished && remaining > 0
    /** Há um descanso em andamento, pausado ou recém-concluído para mostrar. */
    val active get() = running || finished || remaining > 0

    fun start(seconds: Int, label: String = "") {
        duration = seconds.coerceIn(1, MAX_SECONDS) * 1000L
        remaining = duration
        endsAt = now() + remaining
        finished = false
        this.label = label
    }

    /** Soma ou tira segundos do descanso em andamento. Chegar a zero conclui o descanso. */
    fun add(seconds: Int) {
        if (finished || !active) return
        tick()
        if (finished) return
        remaining = (remaining + seconds * 1000L).coerceIn(0, MAX_SECONDS * 1000L)
        if (remaining > duration) duration = remaining
        if (remaining == 0L) {
            endsAt = 0
            finished = true
        } else if (running) endsAt = now() + remaining
    }

    fun toggle() {
        when {
            running -> {
                tick()
                endsAt = 0
            }
            paused -> endsAt = now() + remaining
        }
    }

    fun stop() {
        endsAt = 0
        remaining = 0
        duration = 0
        finished = false
        label = ""
    }

    /** Atualiza o tempo restante. Devolve true no instante em que o descanso termina. */
    fun tick(): Boolean {
        if (!running) return false
        remaining = (endsAt - now()).coerceAtLeast(0)
        if (remaining > 0L) return false
        endsAt = 0
        finished = true
        return true
    }

    companion object {
        const val MAX_SECONDS = 15 * 60
    }
}

/** Diz se o app está na tela, para decidir entre avisar dentro do app ou por notificação. */
object AppState {
    @Volatile var visible = false
}

/**
 * Avisa o fim do descanso mesmo com o app fechado: uma notificação mostra a contagem regressiva e um
 * alarme do sistema dispara a notificação "Descanso concluído".
 */
object RestAlarm {
    private const val ONGOING = "descanso_andamento"
    private const val DONE = "descanso_fim"
    private const val ONGOING_ID = 1
    private const val DONE_ID = 2

    fun schedule(context: Context, remainingMillis: Long, label: String) {
        channels(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancel(DONE_ID)
        runCatching {
            notifications.notify(ONGOING_ID, Notification.Builder(context, ONGOING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Descanso")
                .setContentText(label.ifBlank { "Toque para voltar ao treino" })
                .setWhen(System.currentTimeMillis() + remainingMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context))
                .build())
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + remainingMillis
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm(context))
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm(context))
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm(context))
        }
    }

    /** Cancela o alarme e a contagem regressiva. O aviso de descanso concluído fica até o usuário voltar. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarm(context))
        context.getSystemService(NotificationManager::class.java).cancel(ONGOING_ID)
    }

    /** Tira o aviso de descanso concluído quando o usuário já está de volta ao app. */
    fun clearDone(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(DONE_ID)
    }

    /** O alarme disparou. Com o app na tela, o próprio app avisa; fora dela, entra a notificação. */
    fun finished(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancel(ONGOING_ID)
        if (AppState.visible) return
        channels(context)
        runCatching {
            notifications.notify(DONE_ID, Notification.Builder(context, DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Descanso concluído")
                .setContentText("Hora da próxima série.")
                .setAutoCancel(true)
                .setTimeoutAfter(120_000)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(openApp(context))
                .build())
        }
    }

    /** Vibra e toca um bipe curto quando o descanso termina com o app aberto. */
    fun signal(context: Context) {
        runCatching {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 320), -1))
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            android.os.Handler(context.mainLooper).postDelayed({ tone.release() }, 800)
        }
    }

    private fun channels(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(ONGOING, "Descanso em andamento", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Contagem regressiva do descanso entre séries"
                setShowBadge(false)
            })
        notifications.createNotificationChannel(
            NotificationChannel(DONE, "Descanso concluído", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso quando o descanso termina com o app fora da tela"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 120, 180, 120, 320)
            })
    }

    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 0,
        Intent(context, RestAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openApp(context: Context) = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.finished(context)
}
