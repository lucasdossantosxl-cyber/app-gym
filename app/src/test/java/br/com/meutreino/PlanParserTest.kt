package br.com.meutreino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {
    private fun parse(text: String) = PlanParser.parse(text.trimIndent(), testCatalog)
    private fun ParsedPlan.names() = routines.map { it.name }
    private fun RoutineDraft.refs() = exercises.map { it.ref.ifEmpty { it.name } }

    @Test
    fun markdownWithHeadingsAndBullets() {
        val plan = parse(PLAN_EXAMPLE)
        assertEquals(listOf("Treino A - Peito e tríceps", "Treino B - Costas e bíceps"), plan.names())
        val (a, b) = plan.routines
        assertEquals(listOf("supino-reto", "crucifixo-inclinado", "triceps-corda"), a.refs())
        val bench = a.exercises[0]
        assertEquals("Supino reto com barra", bench.name)
        assertEquals("4", bench.sets)
        assertEquals("12", bench.target)
        assertEquals("40", bench.load)
        assertEquals(90, bench.rest)
        assertEquals("8–12 repetições", bench.note)
        assertEquals("2,5", bench.increment)
        assertEquals(60, a.exercises[2].rest)
        assertEquals("15", a.exercises[2].target)

        assertEquals(listOf("puxada-frente", "remada-baixa", "rosca-direta", "esteira-corrida"), b.refs())
        assertEquals("20", b.exercises[2].load)
        val treadmill = b.exercises[3]
        assertTrue(treadmill.cardio)
        assertEquals("15 min", treadmill.note)
        assertTrue(plan.ignored.isEmpty())
    }

    @Test
    fun markdownTableWithHeader() {
        val plan = parse("""
            ## Treino C – Pernas

            | Exercício | Séries | Repetições | Carga | Descanso |
            |-----------|:------:|-----------:|-------|----------|
            | Agachamento livre | 4 | 8-10 | 60 kg | 2 min |
            | Leg press 45° | 3 | 12 | 120kg | 90s |
            | Cadeira extensora | 3 | 15 |  | 60 |
            | Prancha | 3 | 40s | | |
        """)
        assertEquals(listOf("Treino C – Pernas"), plan.names())
        val exercises = plan.routines.single().exercises
        assertEquals(listOf("agachamento", "leg-press", "cadeira-extensora", "prancha"), plan.routines.single().refs())
        assertEquals(listOf("4", "3", "3", "3"), exercises.map { it.sets })
        assertEquals(listOf("10", "12", "15", ""), exercises.map { it.target })
        assertEquals(listOf("60", "120", "", ""), exercises.map { it.load })
        assertEquals(listOf(120, 90, 60, 0), exercises.map { it.rest })
        assertEquals("8–10 repetições", exercises[0].note)
        assertEquals("40s", exercises[3].note)
    }

    @Test
    fun proseAndNumberedLists() {
        val plan = parse("""
            TREINO B
            1. Rosca direta: 3 séries de 12 repetições
            2) Agachamento livre 4 x 10 (60kg)
            3 - Barra fixa 3x falha
            * **Tríceps testa** — 3×10-12, intervalo de 1min30
            - [ ] 4x10 Supino inclinado
            - Flexão nórdica 3x8
        """)
        assertEquals(listOf("TREINO B"), plan.names())
        val exercises = plan.routines.single().exercises
        assertEquals(listOf("rosca-direta", "agachamento", "barra-fixa", "triceps-testa", "supino-inclinado", "Flexão nórdica"),
            plan.routines.single().refs())
        assertEquals(listOf("3", "4", "3", "3", "4", "3"), exercises.map { it.sets })
        assertEquals(listOf("12", "10", "", "12", "10", "8"), exercises.map { it.target })
        assertEquals("60", exercises[1].load)
        assertEquals("até a falha", exercises[2].note)
        assertEquals(90, exercises[3].rest)
        assertEquals("", exercises[5].ref)
        assertEquals("2,5", exercises[5].increment)
    }

    @Test
    fun columnsExtractedFromPdf() {
        val plan = parse("""
            FICHA DE TREINO - JOÃO
            Treino A
            Exercício Séries Repetições Intervalo
            Supino reto 4 8-12 60s
            Leg press 45 4 12
            Elevação lateral 3 15
            Corrida na esteira 20 min
            Página 1 de 2
        """)
        assertEquals(listOf("Treino A"), plan.names())
        val exercises = plan.routines.single().exercises
        assertEquals(listOf("supino-reto", "leg-press", "elevacao-lateral", "esteira-corrida"), plan.routines.single().refs())
        assertEquals(listOf("4", "4", "3", "1"), exercises.map { it.sets })
        assertEquals(listOf("12", "12", "15", ""), exercises.map { it.target })
        assertEquals(listOf(60, 0, 0, 0), exercises.map { it.rest })
        assertTrue(plan.ignored.any { it.startsWith("Página") })
    }

    @Test
    fun sectionsStayInsideTheirWorkout() {
        val plan = parse("""
            # Plano de hipertrofia
            Faça cada treino duas vezes por semana.

            ## Dia 1 - Superiores
            ### Aquecimento
            - Bicicleta 5 min
            ### Parte principal
            - Supino reto 3x10
            - Remada curvada 3x10

            ## Sexta: pernas
            - Stiff 3x12
            Observações:
            - Beba água
        """)
        assertEquals(listOf("Dia 1 - Superiores", "Sexta: pernas"), plan.names())
        assertEquals(listOf("bicicleta", "supino-reto", "remada-curvada"), plan.routines[0].refs())
        assertEquals(listOf("stiff"), plan.routines[1].refs())
    }

    @Test
    fun headingsWithoutKeywordsBecomeRoutines() {
        val plan = parse("""
            # Meu treino
            ## Peito e tríceps
            - Supino reto 4x10
            ## Costas e bíceps
            - Puxada frontal 4x10
            - Rosca martelo
        """)
        assertEquals(listOf("Peito e tríceps", "Costas e bíceps"), plan.names())
        // linha sem séries só entra quando é um exercício conhecido
        assertEquals(listOf("puxada-frente", "rosca-martelo"), plan.routines[1].refs())
        assertEquals("3", plan.routines[1].exercises[1].sets)
    }

    @Test
    fun timedSetsAndPlainTextWithoutHeading() {
        val plan = parse("""
            Prancha 3x30s
            Abdominal supra 3 x 20 descanso 45 segundos
            Rosca alternada 3x12 cada lado 10 kg
        """)
        assertEquals(listOf("Treino importado"), plan.names())
        val (plank, crunch, curl) = plan.routines.single().exercises
        assertEquals("prancha", plank.ref)
        assertEquals("", plank.target)
        assertEquals("30 s por série", plank.note)
        assertEquals(45, crunch.rest)
        assertEquals("20", crunch.target)
        assertEquals("10", curl.load)
        assertEquals("12", curl.target)
    }

    @Test
    fun textWithoutWorkoutsFindsNothing() {
        assertTrue(parse("Lista de compras:\n- 2 kg de arroz\n- 12 ovos\nLigar para o João às 10h").routines.isEmpty())
        assertTrue(parse("").routines.isEmpty())
        assertTrue(PlanParser.parse("\u0000\u0001 %PDF lixo 3x", testCatalog).routines.isEmpty())
    }
}
