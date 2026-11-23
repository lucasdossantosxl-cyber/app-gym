package br.com.meutreino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {
    private var now = 1_000L
    private val timer = RestTimer { now }

    @Test
    fun countsDownFromTheEndInstant() {
        timer.start(90, "Supino")
        assertTrue(timer.running)
        now += 30_000
        assertFalse(timer.tick())
        assertEquals(60_000, timer.remaining)
        // a tela ficou apagada por muito tempo: o descanso termina no primeiro tick
        now += 10 * 60_000
        assertTrue(timer.tick())
        assertTrue(timer.finished)
        assertFalse(timer.running)
        assertEquals(0, timer.remaining)
        assertFalse(timer.tick())
    }

    @Test
    fun adjustsWhileRunningAndPaused() {
        timer.start(60)
        now += 10_000
        timer.add(15)
        assertEquals(65_000, timer.remaining)
        assertEquals(65_000, timer.duration)
        timer.toggle()
        assertTrue(timer.paused)
        now += 60_000
        assertFalse(timer.tick())
        assertEquals(65_000, timer.remaining)
        timer.add(-15)
        assertEquals(50_000, timer.remaining)
        timer.toggle()
        now += 49_000
        assertFalse(timer.tick())
        now += 1_000
        assertTrue(timer.tick())
    }

    @Test
    fun removingMoreThanIsLeftFinishesTheRest() {
        timer.start(20)
        now += 10_000
        timer.add(-15)
        assertTrue(timer.finished)
        assertFalse(timer.running)
        timer.stop()
        assertFalse(timer.active)
    }
}
