package br.com.meutreino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {
    private var clock = 10_000L
    private val watch = Stopwatch { clock }

    @Test
    fun countsOnlyWhileRunning() {
        assertEquals(0L, watch.elapsed())
        watch.start()
        clock += 1_500
        assertEquals(1_500L, watch.elapsed())
        watch.pause()
        clock += 9_000
        assertEquals(1_500L, watch.elapsed())
        watch.start()
        clock += 500
        assertEquals(2_000L, watch.elapsed())
        assertTrue(watch.running)
    }

    @Test
    fun lapsKeepTheirOwnAndTheTotalTime() {
        watch.lap()
        assertTrue(watch.marks.isEmpty())
        watch.start()
        clock += 30_000
        watch.lap()
        clock += 45_500
        watch.lap()
        clock += 10_000
        assertEquals(listOf(30_000L, 75_500L), watch.marks)
        assertEquals(listOf(30_000L, 45_500L), watch.laps())
        assertEquals(10_000L, watch.currentLap())

        watch.reset()
        assertFalse(watch.running)
        assertEquals(0L, watch.elapsed())
        assertTrue(watch.marks.isEmpty())
    }

    @Test
    fun keepsCountingWhileTheAppIsClosed() {
        watch.start()
        clock += 20_000
        watch.lap()
        clock += 5_000
        val saved = watch.snapshot(wall = 1_000_000)

        // outro processo, com outro relógio monotônico, dois minutos depois
        var later = 500L
        val restored = Stopwatch { later }
        restored.restore(saved, wall = 1_120_000)
        assertTrue(restored.running)
        assertEquals(145_000L, restored.elapsed())
        assertEquals(listOf(20_000L), restored.marks)
        later += 1_000
        assertEquals(146_000L, restored.elapsed())

        // pausado, o tempo fechado não conta
        restored.pause()
        val paused = Stopwatch { 0L }
        paused.restore(restored.snapshot(wall = 2_000_000), wall = 9_000_000)
        assertFalse(paused.running)
        assertEquals(146_000L, paused.elapsed())

        // estado corrompido é ignorado
        val broken = Stopwatch { 0L }
        broken.restore("isto não é JSON", wall = 1)
        assertEquals(0L, broken.elapsed())
    }

    @Test
    fun formatsLikeAPhoneStopwatch() {
        assertEquals("00:00" to "00", stopwatchClock(0))
        assertEquals("01:23" to "45", stopwatchClock(83_450))
        assertEquals("1:01:23" to "09", stopwatchClock(3_683_090))
        assertEquals("01:23,45", stopwatchLabel(83_450))
    }
}
