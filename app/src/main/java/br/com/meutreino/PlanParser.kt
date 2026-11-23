package br.com.meutreino

data class ParsedPlan(val routines: List<RoutineDraft>, val ignored: List<String>)

/**
 * Transforma o texto de uma ficha de treino (Markdown, texto puro ou texto extraído de PDF) em rotinas.
 *
 * Entende títulos como "# Treino A", "Dia 2 - Costas" ou "Peito e tríceps:", listas como
 * "- Supino reto 4x10 40kg descanso 90s", "Rosca direta: 3 séries de 12 repetições", tabelas Markdown com
 * colunas de exercício, séries, repetições, carga e descanso, e colunas de PDF como "Supino reto 4 8-12".
 */
object PlanParser {
    private const val RANGE = """(?:\s*(?:-|–|—|a|à|ate|até)\s*(\d{1,3}))?"""
    private const val SECONDS = """(\s*(?:s|seg|segs|segundos?|"|”)(?![a-zà-ú]))?"""
    private val opt = setOf(RegexOption.IGNORE_CASE)

    private val SETS_X_REPS = Regex("""(\d{1,2})\s*(?:x|×|\*)\s*(\d{1,3})$RANGE$SECONDS""", opt)
    private val SERIES_OF = Regex(
        """(\d{1,2})\s*(?:séries?|series?|sets?)\s*(?:de|x|×|com)?\s*(\d{1,3})$RANGE$SECONDS""", opt)
    private val SETS_OPEN = Regex(
        """(\d{1,2})\s*(?:x|×)\s*(?:até\s+a\s+|ate\s+a\s+)?(falha|máx(?:imo)?|max(?:imo)?|amrap)""", opt)
    private val SERIES_ONLY = Regex("""(\d{1,2})\s*(?:séries?|series?|sets?)(?![a-zà-ú])""", opt)
    private val REPS_ONLY = Regex("""(\d{1,3})$RANGE\s*(?:reps?|repetiç(?:ão|ões)|repeticoes|repeticao)(?![a-zà-ú])""", opt)
    private val COLUMNS = Regex("""(?<=\s)(\d{1,2})\s+(\d{1,3})(?:\s*[-–]\s*(\d{1,3}))?(?=\s|$)""")
    private val LOAD = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*(kg|kgs|quilos?|lbs?)(?![a-zà-ú])""", opt)
    private val REST = Regex(
        """(?:descanso|intervalo|pausa|rest|desc\.|int\.)\s*(?:de\s+|:|=|-)?\s*(\d{1,3}(?:\s*(?:min(?:utos?)?|m|['′])(?:\s*(?:e\s*)?\d{1,2})?|\s*(?:s|seg|segs|segundos?|"|”))?)(?![a-zà-ú])""",
        opt)
    private val REST_AFTER = Regex(
        """(\d{1,3})\s*(s|seg|segs|segundos?|"|”|min(?:utos?)?|['′])\s*(?:de\s+)?(?:descanso|intervalo|pausa|rest)""", opt)
    private val REST_TRAILING = Regex("""(?<=\s)(\d{2,3})\s*(?:s|seg|segs|segundos?|"|”)\s*$""", opt)
    private val MINUTES = Regex("""(\d{1,3})\s*(?:min(?:utos?)?|['′])(?![a-zà-ú])""", opt)
    private val DISTANCE = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*km(?![a-zà-ú])""", opt)

    private val ROUTINE_KEYWORD = Regex(
        """^(?:(?:treino|ficha|rotina|dia|day|workout|sess[aã]o)\s+(?:[a-z]|\d{1,2})(?![a-zà-ú0-9])|(?:segunda|ter[cç]a|quarta|quinta|sexta|s[aá]bado|domingo)(?![a-zà-ú])|[a-f]\s*[-–—:)]\s)""",
        opt)
    private val SECTION = Regex(
        """^(?:aquecimento|parte principal|principal|alongamentos?|finaliza[cç][aã]o|desaquecimento|mobilidade|observa[cç][oõ]es|obs|notas?|dicas?|legenda|orienta[cç][oõ]es|instru[cç][oõ]es)(?![a-zà-ú])""",
        opt)
    private val CARDIO_WORDS = Regex(
        """esteira|bicicleta|bike|corrida|caminhada|el[ií]ptico|transport|escada|pular corda|remo erg|spinning|hiit|nata[cç][aã]o|cardio|aer[oó]bico""",
        opt)

    private class Building(val name: String, val level: Int, val keyword: Boolean) {
        val exercises = mutableListOf<Exercise>()
    }

    private class Columns(val name: Int, val sets: Int, val reps: Int, val load: Int, val rest: Int, val note: Int)

    fun parse(text: String, catalog: Catalog): ParsedPlan {
        val routines = mutableListOf<Building>()
        val ignored = mutableListOf<String>()
        var current: Building? = null
        var columns: Columns? = null

        fun target(): Building = current ?: Building("Treino importado", 99, false).also {
            routines += it
            current = it
        }

        fun heading(title: String, level: Int) {
            val keyword = ROUTINE_KEYWORD.containsMatchIn(title)
            val open = current
            val section = open != null && (SECTION.containsMatchIn(title) ||
                (!keyword && open.keyword && level > open.level))
            if (!section) current = Building(title, level, keyword).also { routines += it }
        }

        for (raw in text.lineSequence().take(4000)) {
            val line = raw.replace('\t', ' ').replace(' ', ' ').trim()
            if (line.isEmpty() || line.all { it in "-=_*~ " }) continue

            if (line.startsWith("|") || line.count { it == '|' } >= 2) {
                val cells = line.trim('|').split('|').map(::clean)
                if (cells.all { cell -> cell.isEmpty() || cell.all { it in "-: " } }) continue
                val header = header(cells)
                if (header != null) {
                    columns = header
                    continue
                }
                val exercise = columns?.let { fromCells(cells, it, catalog) }
                    ?: exercise(cells.filter { it.isNotEmpty() }.joinToString(" "), catalog, current != null)
                if (exercise != null) target().exercises += exercise else ignored += line
                continue
            }
            columns = null

            if (line.startsWith("#")) {
                val title = clean(line.trimStart('#')).trimEnd(':').trim()
                if (title.isNotEmpty()) heading(title, line.takeWhile { it == '#' }.length)
                continue
            }

            val content = clean(line)
            if (content.isEmpty()) continue
            val hasPlan = SETS_X_REPS.containsMatchIn(content) || SERIES_OF.containsMatchIn(content) ||
                SETS_OPEN.containsMatchIn(content) || SERIES_ONLY.containsMatchIn(content)
            if (!hasPlan && content.length <= 60 && (ROUTINE_KEYWORD.containsMatchIn(content) ||
                    (content.endsWith(":") && content.none { it.isDigit() }))) {
                heading(content.trimEnd(':').trim(), 50)
                continue
            }

            val exercise = exercise(content, catalog, current != null)
            if (exercise != null) target().exercises += exercise else ignored += line
        }

        return ParsedPlan(
            routines.filter { it.exercises.isNotEmpty() }.map { RoutineDraft(name = it.name.take(60), exercises = it.exercises) },
            ignored.take(60)
        )
    }

    /** Tira marcadores de lista, numeração, caixas de seleção e ênfase do Markdown. */
    private fun clean(text: String): String = text.trim()
        .replace(Regex("""^(?:>\s*)+"""), "")
        .replace(Regex("""^(?:[-*+•·▪●○➤→]\s+|\d{1,2}\s*[.)ºª]\s+|\d{1,2}\s*-\s+(?=\D))"""), "")
        .replace(Regex("""^\[[ xX]?]\s*"""), "")
        .replace(Regex("""\*\*|__|`|~~"""), "")
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""(?<![\w*])\*(?=\S)|(?<=\S)\*(?![\w*])"""), "")
        .trim()

    private fun header(cells: List<String>): Columns? {
        val names = cells.map { it.normalized() }
        fun find(vararg words: String) = names.indexOfFirst { cell -> words.any { cell.startsWith(it) } }
        val name = find("exercicio", "exercise", "movimento", "atividade", "nome")
        if (name < 0) return null
        return Columns(name, find("serie", "sets", "set"), find("rep"), find("carga", "peso", "kg", "load", "weight"),
            find("descanso", "intervalo", "pausa", "rest"), find("obs", "nota", "note", "tecnica", "comentario"))
    }

    private fun fromCells(cells: List<String>, columns: Columns, catalog: Catalog): Exercise? {
        fun cell(index: Int) = cells.getOrNull(index)?.trim().orEmpty()
        val name = cell(columns.name)
        if (name.count { it.isLetter() } < 3) return null
        if (columns.sets < 0 && columns.reps < 0) return exercise(cells.joinToString(" "), catalog, true)
        val sets = Regex("""\d{1,2}""").find(cell(columns.sets))?.value
        val reps = Regex("""(\d{1,3})(?:\s*(?:-|–|—|a|até)\s*(\d{1,3}))?""").find(cell(columns.reps))
        val timed = reps != null && Regex("""\d\s*(?:s|seg|segundos?|"|min)""", opt).containsMatchIn(cell(columns.reps))
        val load = LOAD.find(cell(columns.load)) ?: Regex("""(\d{1,3}(?:[.,]\d{1,2})?)()""").find(cell(columns.load))
        val rest = duration(cell(columns.rest))
        val notes = listOfNotNull(
            reps?.takeIf { it.groupValues[2].isNotEmpty() && !timed }?.let { "${it.groupValues[1]}–${it.groupValues[2]} repetições" },
            cell(columns.reps).takeIf { timed || (reps == null && it.isNotEmpty()) },
            cell(columns.note).takeIf { it.isNotEmpty() })
        return build(name, catalog, sets, if (timed) null else reps?.let { it.groupValues[2].ifEmpty { it.groupValues[1] } },
            load?.groupValues?.get(1), load?.groupValues?.get(2), rest, notes, null)
    }

    private fun exercise(line: String, catalog: Catalog, insideRoutine: Boolean): Exercise? {
        val spans = mutableListOf<IntRange>()
        var sets: String? = null
        var reps: String? = null
        val notes = mutableListOf<String>()

        fun plan(match: MatchResult) {
            spans += match.range
            sets = match.groupValues[1]
            val low = match.groupValues[2]
            val high = match.groupValues[3]
            if (match.groupValues[4].isNotBlank()) notes += "$low s por série"
            else {
                reps = high.ifEmpty { low }
                if (high.isNotEmpty()) notes += "$low–$high repetições"
            }
        }

        val open = SETS_OPEN.find(line)
        val pair = SETS_X_REPS.find(line) ?: SERIES_OF.find(line)
        when {
            pair != null -> plan(pair)
            open != null -> {
                spans += open.range
                sets = open.groupValues[1]
                notes += "até a ${if (open.groupValues[2].lowercase().startsWith("f")) "falha" else "máxima"}"
            }
            else -> {
                SERIES_ONLY.find(line)?.let {
                    spans += it.range
                    sets = it.groupValues[1]
                }
                REPS_ONLY.find(line)?.let {
                    spans += it.range
                    reps = it.groupValues[2].ifEmpty { it.groupValues[1] }
                    if (it.groupValues[2].isNotEmpty()) notes += "${it.groupValues[1]}–${it.groupValues[2]} repetições"
                }
            }
        }

        var rest: Int? = null
        REST.find(line)?.let {
            spans += it.range
            rest = duration(it.groupValues[1])
        } ?: REST_AFTER.find(line)?.let {
            spans += it.range
            rest = duration(it.groupValues[1] + it.groupValues[2])
        }

        val load = LOAD.findAll(line).firstOrNull { match -> spans.none { match.range.first in it } }
        load?.let { spans += it.range }

        if (sets == null && reps == null) {
            // colunas de PDF: "Supino reto 4 8-12"
            // "Leg press 45 4 12": o primeiro par (45, 4) não é um plano plausível, então tenta o seguinte
            var columns = COLUMNS.find(line)
            while (columns != null && !(spans.none { span -> columns!!.range.first in span } &&
                    columns.groupValues[1].toInt() in 1..10 && columns.groupValues[2].toInt() in 1..100)) {
                columns = COLUMNS.find(line, columns.range.first + 1)
            }
            columns?.let {
                spans += it.range
                sets = it.groupValues[1]
                reps = it.groupValues[3].ifEmpty { it.groupValues[2] }
                if (it.groupValues[3].isNotEmpty()) notes += "${it.groupValues[2]}–${it.groupValues[3]} repetições"
            }
        }

        if (rest == null && sets != null) {
            // coluna de intervalo sem rótulo no fim da linha: "Supino reto 4 8-12 60s"
            REST_TRAILING.find(line)?.takeIf { match -> spans.none { match.range.first in it } }?.let {
                spans += it.range
                rest = duration(it.groupValues[1] + "s")
            }
        }

        val minutes = MINUTES.findAll(line).firstOrNull { match -> spans.none { match.range.first in it } }
        val distance = DISTANCE.find(line)
        val first = spans.minOfOrNull { it.first } ?: listOfNotNull(minutes, distance).minOfOrNull { it.range.first } ?: line.length
        var name = trimName(line.substring(0, first))
        if (name.count { it.isLetter() } < 3) {
            // "4x10 Supino reto": o nome vem depois do plano
            val last = (spans.maxOfOrNull { it.last } ?: -1) + 1
            name = trimName(line.substring(last.coerceAtMost(line.length)))
        }
        if (name.count { it.isLetter() } < 3 || name.length > 70) return null

        val item = catalog.match(name)
        val cardio = item?.cardio == true ||
            (item == null && sets == null && reps == null && CARDIO_WORDS.containsMatchIn(name))
        if (cardio) {
            val plan = listOfNotNull(minutes?.let { "${it.groupValues[1]} min" }, distance?.let { "${it.groupValues[1]} km" })
            return build(name, catalog, null, null, null, null, rest, plan + notes, true)
        }
        if (sets == null && reps == null) {
            // linha sem plano só vira exercício quando é claramente um item da biblioteca dentro de um treino
            if (!insideRoutine || item == null || name.split(' ').size > 7) return null
            if (item.name.normalized() != name.normalized() && item.aliases.none { it == name.normalized() }) return null
        }
        return build(name, catalog, sets, reps, load?.groupValues?.get(1), load?.groupValues?.get(2), rest, notes, false)
    }

    private fun trimName(text: String) = text
        .replace(Regex("""\(\s*$"""), "")
        .trim { it.isWhitespace() || it in "-–—:;,.|/\\()[]=>•" }

    /** "90s", "1 min", "1min30", "2'", "45" (segundos) ou "2" (minutos). */
    private fun duration(text: String): Int? {
        val value = text.trim().lowercase()
        if (value.isEmpty()) return null
        Regex("""(\d{1,3})\s*(?:min(?:utos?)?|m|['′])\s*(?:e\s*)?(\d{1,2})?""").find(value)?.let {
            return (it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0)).coerceIn(5, 900)
        }
        val number = Regex("""\d{1,3}""").find(value)?.value?.toInt() ?: return null
        val seconds = if (Regex("""\d\s*(?:s|seg|"|”)""").containsMatchIn(value) || number > 5) number else number * 60
        return seconds.coerceIn(5, 900)
    }

    private fun build(
        name: String, catalog: Catalog, sets: String?, reps: String?, load: String?, unit: String?,
        rest: Int?, notes: List<String>, cardio: Boolean?
    ): Exercise {
        val item = catalog.match(name)
        val isCardio = cardio ?: (item?.cardio == true)
        val base = item?.toExercise() ?: Exercise(name = name.replaceFirstChar { it.uppercase() }, cardio = isCardio,
            unit = if (isCardio) "km" else "kg", increment = if (isCardio) "" else "2,5")
        val note = notes.filter { it.isNotBlank() }.distinct().joinToString(" · ").take(120)
        if (isCardio) return base.copy(cardio = true, unit = "km", sets = "1", target = "", increment = "",
            rest = 0, note = note)
        return base.copy(
            cardio = false,
            unit = when {
                unit == null || unit.isEmpty() -> base.unit
                unit.lowercase().startsWith("lb") -> "lb"
                else -> "kg"
            },
            sets = sets?.toIntOrNull()?.coerceIn(1, 20)?.toString() ?: "3",
            target = reps?.toIntOrNull()?.takeIf { it in 1..200 }?.toString().orEmpty(),
            rest = rest ?: 0,
            load = load?.takeIf { it.decimal() != null }?.replace('.', ',').orEmpty(),
            note = note
        )
    }
}
