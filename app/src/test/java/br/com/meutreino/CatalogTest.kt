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
        assertTrue(testCatalog.items.size >= 270)
        assertTrue(testCatalog.items.count { it.photo == it.id } >= 250)
        testCatalog.items.forEach { item ->
            // quem tem foto (própria ou de um exercício parecido) precisa dos dois arquivos
            if (item.photo.isNotEmpty()) {
                assertTrue(item.id, File("src/main/assets/ex/${item.photo}_0.jpg").length() > 2_000)
                assertTrue(item.id, File("src/main/assets/ex/${item.photo}_1.jpg").length() > 2_000)
            }
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
        assertEquals("flexao-nordica", id("Flexão nórdica"))
        assertEquals("triceps-katana", id("Tríceps katana"))
        assertEquals("natacao", id("Natação"))
        assertNull(id("Levantamento de tora"))
        assertNull(id("Observações gerais do treino"))
        assertNull(id(""))
    }

    @Test
    fun variationsKeepTheLibraryReference() {
        fun found(name: String) = testCatalog.matchVariant(name)?.let { it.first.id to it.second }
        // sem foto própria, mas com instruções: a variação fica separada do exercício base
        assertEquals("triceps-katana" to "unilateral", found("Tríceps katana na polia unilateral"))
        assertEquals("triceps-corda" to "unilateral", found("Tríceps corda unilateral"))
        assertEquals("rosca-scott" to "alternada", found("Rosca Scott alternada"))
        assertEquals("rosca-martelo-alternada" to "", found("Rosca martelo alternada"))
        // exercícios da biblioteca que já são a variação continuam como estão
        assertEquals("rosca-alternada" to "", found("Rosca alternada"))
        assertEquals("remada-unilateral" to "", found("Remada unilateral com halter"))
        assertEquals("leg-press-unilateral" to "", found("Leg press unilateral"))
        assertEquals("supino-reto" to "", found("Supino reto"))
        assertNull(found("Levantamento de tora unilateral"))

        val base = testCatalog["triceps-corda"]!!.toExercise()
        val variant = base.withVariant("Unilateral", testCatalog)
        assertEquals("Tríceps na polia com corda · unilateral", variant.name)
        assertEquals("triceps-corda", variant.ref)
        assertTrue(base.key() != variant.key())
        assertEquals(base.name, variant.withVariant("", testCatalog).name)
        assertEquals(base.key(), variant.withVariant("", testCatalog).key())

        // exercício criado à mão também aceita variação e volta ao nome original
        val custom = Exercise(name = "Meu exercício").withVariant("com pausa", testCatalog)
        assertEquals("Meu exercício · com pausa", custom.name)
        assertEquals("Meu exercício", custom.withVariant("", testCatalog).name)
    }

    @Test
    fun photosPointToExistingFiles() {
        assertEquals("", testCatalog["triceps-katana"]!!.photo)
        assertEquals("leg-press", testCatalog["leg-press-unilateral"]!!.photo)
        assertEquals("leg-press", testCatalog.photos["leg-press-unilateral"])
        assertNull(testCatalog.photos["triceps-katana"])
    }

    @Test
    fun searchFiltersByTextAndGroup() {
        assertTrue(testCatalog.search("supino", null).all { "supino" in it.name.normalized() || it.aliases.any { a -> "supino" in a } })
        assertTrue(testCatalog.search("", "Bíceps").all { it.group == "Bíceps" })
        assertEquals(listOf("rosca-martelo", "rosca-martelo-corda", "rosca-martelo-alternada", "rosca-martelo-cruzada"),
            testCatalog.search("martelo", "Bíceps").map { it.id })
        assertTrue(testCatalog.search("halteres peito", null).isNotEmpty())
        assertTrue(testCatalog.search("xyzabc", null).isEmpty())
    }
}
