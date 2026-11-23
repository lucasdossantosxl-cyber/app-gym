package br.com.meutreino

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

val testCatalog: Catalog by lazy { Catalog.parse(File("src/main/assets/exercises.json").readText()) }

class CatalogTest {
    @Test
    fun everyExerciseHasBothPhotosAndInstructions() {
        assertTrue(testCatalog.items.size >= 120)
        assertEquals(testCatalog.items.size, testCatalog.items.map { it.id }.toSet().size)
        testCatalog.items.forEach { item ->
            assertTrue(item.id, File("src/main/assets/ex/${item.id}_0.jpg").length() > 2_000)
            assertTrue(item.id, File("src/main/assets/ex/${item.id}_1.jpg").length() > 2_000)
            assertTrue(item.id, item.how.length > 30)
            assertEquals(item.id, item.group == "Cardio", item.cardio)
        }
    }

    @Test
    fun matchesNamesWrittenInDifferentWays() {
        fun id(name: String) = testCatalog.match(name)?.id
        assertEquals("supino-reto", id("Supino reto"))
        assertEquals("supino-reto", id("SUPINO RETO COM BARRA"))
        assertEquals("supino-inclinado-halteres", id("Supino inclinado c/ halteres".replace("c/", "com")))
        assertEquals("puxada-frente", id("Puxada alta aberta"))
        assertEquals("triceps-corda", id("Tríceps na corda"))
        assertEquals("leg-press", id("Leg Press 45º"))
        assertEquals("rosca-direta", id("Rosca direta"))
        assertEquals("cadeira-extensora", id("Extensora"))
        assertEquals("elevacao-lateral", id("Elevação lateral (halteres)"))
        assertEquals("esteira-corrida", id("Esteira"))
        assertNull(id("Flexão nórdica"))
        assertNull(id("Observações gerais do treino"))
        assertNull(id(""))
    }

    @Test
    fun searchFiltersByTextAndGroup() {
        assertTrue(testCatalog.search("supino", null).all { "supino" in it.name.normalized() || it.aliases.any { a -> "supino" in a } })
        assertTrue(testCatalog.search("", "Bíceps").all { it.group == "Bíceps" })
        assertEquals(listOf("rosca-martelo", "rosca-martelo-corda"), testCatalog.search("martelo", "Bíceps").map { it.id })
        assertTrue(testCatalog.search("halteres peito", null).isNotEmpty())
        assertTrue(testCatalog.search("xyzabc", null).isEmpty())
    }
}
