package setups.importer

import setups.model.Gears
import setups.model.Setup

/** Something the importer wasn't sure about, for a human to check. */
data class Issue(val line: Int, val message: String, val context: String = "")

data class ParsedSetup(val series: String, val setup: Setup, val line: Int)

data class ParseResult(val setups: List<ParsedSetup>, val issues: List<Issue>)

/**
 * Parses the loosely formatted setup text found on old fan sites, e.g.
 *
 * ```
 * Bristol Setups
 * Tire Pressure: 20
 * Fender Flare: 76 in.
 * Wedge: +0.5
 * Gears: 1.) 3.60  2.) 2.30  3.) 1.80  4.) 1.39  Rear End.) 3.50
 * Setup by: ZebFAN06
 * ```
 *
 * Handles both layouts in the source files: author *after* each setup ("Setup by: X"),
 * and author heading a whole section ("Setups by X" / "X's Setups").
 *
 * @param resolveTrack maps a heading such as "Lowes Motor Speedway (Charlotte) Setups" to a track id, or null.
 */
class TxtParser(private val resolveTrack: (String) -> String?) {

    private enum class Kind {
        TIRE, FENDER, SPOILER, FRONT, REAR, SWAY, WEDGE, GEARS, GEAR_PRESET, GEARS_CONT, BEST_LAP,
        /** "Down Force: FF 75in; RS 70°" (fender flare + rear spoiler on one line) */
        DOWNFORCE,
        /** "Suspension: FS 63%; RS 68%; SB 7/8in; W +1.0" (springs, sway bar, wedge on one line) */
        SUSPENSION,
    }

    private val labels: List<Pair<Regex, Kind>> = listOf(
        """rear\s*end""" to Kind.GEARS_CONT,
        """gear\s*ratios?""" to Kind.GEARS,
        """gear\s*preset""" to Kind.GEAR_PRESET,
        """gears?(?![a-z])""" to Kind.GEARS,
        """tire\s*pressure""" to Kind.TIRE,
        """fender\s*flare""" to Kind.FENDER,
        """rear\s*spoiler""" to Kind.SPOILER,
        """front\s*(?:spr[a-z]*|suspension)|front(?![a-z])""" to Kind.FRONT,
        """rear\s*(?:spr[a-z]*|suspension)|rear(?![a-z])""" to Kind.REAR,
        """sway\s*bar[a-z]*""" to Kind.SWAY,
        """wedge|wg(?![a-z])""" to Kind.WEDGE,
        """best\s*lap""" to Kind.BEST_LAP,
        """down\s*force""" to Kind.DOWNFORCE,
        """suspension""" to Kind.SUSPENSION,
    ).map { (pattern, kind) -> Regex("""^\s*($pattern)(.*)$""", RegexOption.IGNORE_CASE) to kind }

    private class Entry(val startLine: Int) {
        var tire: Int? = null
        var fender: Double? = null
        var spoiler: Int? = null
        var front: Int? = null
        var rear: Int? = null
        var sway: String? = null
        var wedge: Double? = null
        var gears: Gears? = null
        var bestLap: String? = null
        val notes = mutableListOf<String>()
        val raw = mutableListOf<String>()
        var author: String? = null
        val seen = mutableSetOf<Kind>()
        fun isEmpty() = seen.isEmpty()
    }

    private val issues = mutableListOf<Issue>()
    private val out = mutableListOf<ParsedSetup>()
    private var series = "cup"
    private var track: String? = null
    private var sectionAuthor: String? = null
    private var entry: Entry? = null

    fun parse(text: String): ParseResult {
        issues.clear(); out.clear()
        series = "cup"; track = null; sectionAuthor = null; entry = null

        text.lines().forEachIndexed { i, rawLine ->
            val lineNo = i + 1
            // Source files contain tabs and non-breaking spaces as "blank" lines.
            val line = rawLine.replace(' ', ' ').trim()
            if (line.isEmpty()) return@forEachIndexed
            handleLine(line, lineNo)
        }
        flush()
        return ParseResult(out.toList(), issues.toList())
    }

    private fun handleLine(line: String, lineNo: Int) {
        val lower = line.lowercase()

        // Noise from the web pages the text was copied from.
        if (lower.endsWith(".jpg") || lower == "add your content here") return

        // Series headings.
        if ("featherlite modified" in lower) {
            flush(); series = "modified"; track = null; sectionAuthor = null; return
        }
        if ("nextel cup series" in lower || "winston cup series" in lower) {
            flush(); series = "cup"; return
        }
        if ("national series" in lower || "busch series" in lower) {
            // In the 2005 dump this heading sits directly under the Cup heading with no setups of its own.
            issues += Issue(lineNo, "Ignored series heading; following setups kept in the current series ($series)", line)
            return
        }

        // Section author headings: "Setups by Warhawk", "Chadlyroush's Setups".
        Regex("""^setups\s+by:?\s*(.+)$""", RegexOption.IGNORE_CASE).find(line)?.let {
            flush(); sectionAuthor = it.groupValues[1].trim(); return
        }
        Regex("""^(.+?)'s\s+setups$""", RegexOption.IGNORE_CASE).find(line)?.let {
            flush(); sectionAuthor = it.groupValues[1].trim(); return
        }

        // Per-setup author trailer: "Setup by: X", "Setup by X", "Gear Setup by: Daryl".
        Regex("""^(?:gear\s+)?setup\s+by:?\s*(.+)$""", RegexOption.IGNORE_CASE).find(line)?.let {
            val e = entry
            if (e == null || e.isEmpty()) {
                issues += Issue(lineNo, "Author line with no setup before it", line)
            } else {
                e.author = it.groupValues[1].trim()
                e.raw += line
            }
            flush(); return
        }

        // A bare "15 PSI" line means tire pressure.
        Regex("""^(\d+)\s*psi$""", RegexOption.IGNORE_CASE).find(line)?.let {
            setField(Kind.TIRE, it.groupValues[1], line, lineNo); return
        }

        // Parenthesised remarks belong to the current setup.
        if (line.startsWith("(") && line.endsWith(")")) {
            current(lineNo).apply { notes += line.removeSurrounding("(", ")"); raw += line }
            return
        }

        for ((regex, kind) in labels) {
            val m = regex.find(line) ?: continue
            val rest = m.groupValues[2]
            setField(kind, if (kind == Kind.GEARS_CONT) line else rest, line, lineNo)
            return
        }

        // Track headings, possibly followed by race names: "Daytona - Daytona 500/Pepsi 400".
        (resolveTrack(line) ?: line.split(" - ").takeIf { it.size > 1 }?.let { resolveTrack(it[0]) })?.let { id ->
            flush(); track = id; return
        }

        issues += Issue(lineNo, "Unrecognised line (skipped)", line)
    }

    private fun current(lineNo: Int): Entry = entry ?: Entry(lineNo).also { entry = it }

    private fun setField(kindIn: Kind, valueIn: String, line: String, lineNo: Int) {
        var kind = kindIn
        // A repeated field means a new setup started without an author line in between.
        val existing = entry
        val repeatable = kind == Kind.GEARS_CONT || kind == Kind.GEARS || kind == Kind.GEAR_PRESET
        if (existing != null && !repeatable && kind in existing.seen) {
            // "Rear Spoiler: 75%" after a spoiler was already given is really rear springs.
            if (kind == Kind.SPOILER && "%" in valueIn && Kind.REAR !in existing.seen) {
                issues += Issue(lineNo, "Read 'Rear Spoiler' with a % value as Rear Springs", line)
                kind = Kind.REAR
            } else {
                flush()
            }
        }
        val e = current(lineNo)
        e.raw += line
        e.seen += kind
        applyField(e, kind, valueIn, line, lineNo)
    }

    /** @param hasSeparator false for the parts of a combined line ("W -1.5"), where a leading "-" is a sign. */
    private fun applyField(e: Entry, kind: Kind, valueIn: String, line: String, lineNo: Int, hasSeparator: Boolean = true) {
        val (value, separator) = if (hasSeparator) splitSeparator(valueIn) else valueIn to null
        val v = value.trim().trimEnd(':').trim()
        val lv = v.lowercase()
        fun flag(msg: String) { issues += Issue(lineNo, msg, line) }

        when (kind) {
            Kind.DOWNFORCE, Kind.SUSPENSION -> {
                if (isNa(lv)) return // plate tracks: no fender flare or spoiler adjustments
                v.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                    val m = Regex("""^(ff|rs|fs|sb|w)\s+(.*)$""", RegexOption.IGNORE_CASE).find(part)
                    // "RS" is the rear spoiler on the Down Force line and rear springs on the Suspension line.
                    val sub = when (m?.groupValues?.get(1)?.lowercase()) {
                        "ff" -> Kind.FENDER
                        "rs" -> if (kind == Kind.DOWNFORCE) Kind.SPOILER else Kind.REAR
                        "fs" -> Kind.FRONT
                        "sb" -> Kind.SWAY
                        "w" -> Kind.WEDGE
                        else -> null
                    }
                    if (m == null || sub == null) {
                        flag("Unrecognised part '$part'")
                    } else {
                        e.seen += sub
                        applyField(e, sub, m.groupValues[2], line, lineNo, hasSeparator = false)
                    }
                }
            }
            Kind.TIRE -> {
                e.tire = firstNumber(v)?.toInt()
                if (e.tire == null) flag("Tire pressure has no number")
                else if (!Regex("""^\d+(\s*psi)?\.?$""").matches(lv)) flag("Unusual tire pressure value")
            }
            Kind.FENDER -> when {
                isNa(lv) -> e.fender = null
                lv == "?" -> e.notes += "Fender flare not given"
                else -> {
                    e.fender = firstNumber(v)
                    if (e.fender == null || !Regex("""^\d+(\.\d+)?(\s+1/2)?\s*(in\.?|inches|n\.)?$""").matches(lv))
                        flag("Unusual fender flare value")
                }
            }
            Kind.SPOILER -> when {
                isNa(lv) -> e.spoiler = null
                lv == "?" -> e.notes += "Rear spoiler not given"
                else -> {
                    e.spoiler = firstNumber(v)?.toInt()
                    if (e.spoiler == null || !Regex("""^\d+\s*(degrees|°)?$""").matches(lv)) flag("Unusual rear spoiler value")
                }
            }
            Kind.FRONT, Kind.REAR -> {
                val n = firstNumber(v)?.toInt()
                if (kind == Kind.FRONT) e.front = n else e.rear = n
                if (n == null || !Regex("""^\d+\s*%?$""").matches(lv)) flag("Unusual spring value")
            }
            Kind.SWAY -> {
                val m = Regex("""(\d+\s+\d/\d|\d/\d|\d+)""").find(v)
                e.sway = m?.groupValues?.get(1)?.replace(Regex("""\s+"""), " ")?.let { "$it in" }
                if (e.sway == null) flag("Sway bar has no value")
            }
            Kind.WEDGE -> parseWedge(e, v, separator, line, lineNo)
            Kind.GEARS, Kind.GEARS_CONT -> parseGears(e, v, line, lineNo, presetLabel = false)
            Kind.GEAR_PRESET -> parseGears(e, v, line, lineNo, presetLabel = true)
            Kind.BEST_LAP -> e.bestLap = v
        }
    }

    /** Splits a leading ":" / "-" / "=" separator from the value. Returns (value, separator). */
    private fun splitSeparator(s: String): Pair<String, Char?> {
        val t = s.trimStart()
        return when {
            t.startsWith(":") -> t.trimStart(':') to ':'
            t.startsWith("-") -> t.substring(1) to '-'
            t.startsWith("=") -> t.substring(1) to '='
            else -> t to null
        }
    }

    private fun parseWedge(e: Entry, v: String, separator: Char?, line: String, lineNo: Int) {
        var s = v
        // "-.0.5" is a typo for -0.5.
        Regex("""^([+-]?)\s*\.(\d)\.(\d+)""").find(s)?.let {
            s = "${it.groupValues[1]}${it.groupValues[2]}.${it.groupValues[3]}" + s.substring(it.range.last + 1)
            issues += Issue(lineNo, "Wedge typo corrected", line)
        }
        val m = Regex("""^([+-])?\s*(\d*\.?\d+)""").find(s.trim())
        if (m == null) {
            issues += Issue(lineNo, "Wedge has no number", line); return
        }
        val sign = if (m.groupValues[1] == "-") -1 else 1
        e.wedge = sign * m.groupValues[2].toDouble()
        if (separator == '-' && m.groupValues[1].isEmpty() && e.wedge != 0.0) {
            issues += Issue(lineNo, "Wedge written as 'Wedge - N': read as +N, but it may mean -N", line)
        }
        // "Wedge: 0.5 Turns Rear - 3.30": a rear end gear on the wedge line.
        Regex("""rear\s*-\s*(\d\.\d+)""", RegexOption.IGNORE_CASE).find(s)?.let {
            val g = e.gears ?: Gears()
            e.gears = g.copy(rearEnd = it.groupValues[1].toDouble())
            issues += Issue(lineNo, "Rear end gear found on the wedge line", line)
        }
    }

    private fun parseGears(e: Entry, v: String, line: String, lineNo: Int, presetLabel: Boolean) {
        val lv = v.lowercase()
        val old = e.gears ?: Gears()
        val ratios = (old.ratios ?: List(4) { null }).toMutableList<Double?>()
        var preset = old.preset
        var rearEnd = old.rearEnd
        var found = false

        if (presetLabel) {
            firstNumber(v)?.let { preset = it.toInt(); found = true }
        }
        Regex("""preset\s*:?\s*(\d+)""").find(lv)?.let { preset = it.groupValues[1].toInt(); found = true }

        // "1.) 3.60", "1) 3.6", "4.) Default", "Gear 4.) 0.98" (here v is "4.) 0.98")
        Regex("""(?:^|\s)([1-5])\s*\.?\)\s*(default|\d*\.?\d+)""").findAll(lv).forEach { m ->
            val idx = m.groupValues[1].toInt()
            val value = m.groupValues[2]
            found = true
            if (idx > 4) {
                issues += Issue(lineNo, "Gear $idx listed; these games only have 4 forward gears", line)
            } else if (value != "default") {
                ratios[idx - 1] = value.toDouble()
            }
        }
        // "4th 1.11", "4th=1.59", "only change 4th to .81"
        Regex("""([1-4])(?:st|nd|rd|th)\s*(?:=|to)?\s*(\d*\.\d+|\d+\.\d*)""").findAll(lv).forEach { m ->
            ratios[m.groupValues[1].toInt() - 1] = m.groupValues[2].toDouble(); found = true
        }
        // "Rear End.) 3.55", "Rear End: 3.45", "RE) 3.5", "rear end 3.65"
        Regex("""(?:rear\s*end|\bre)\s*[.:)]*\s*(use default|default|\d*\.?\d+)""").find(lv)?.let { m ->
            val value = m.groupValues[1]
            if (!value.contains("default")) rearEnd = value.toDouble()
            found = true
        }

        var useDefault = old.useDefault
        if (!found && "default" in lv) { useDefault = true; found = true }

        if (!found) {
            issues += Issue(lineNo, "Could not read gears", line)
            e.notes += "Gears: $v"
        }
        if (presetLabel || "preset" in lv) {
            if (preset == null) issues += Issue(lineNo, "Gear preset with no number", line)
        }
        e.gears = Gears(
            preset = preset,
            ratios = ratios.takeIf { r -> r.any { it != null } },
            rearEnd = rearEnd,
            useDefault = useDefault,
        ).takeIf { it != Gears() }
    }

    private fun flush() {
        val e = entry ?: return
        entry = null
        if (e.isEmpty()) {
            if (e.raw.isNotEmpty()) issues += Issue(e.startLine, "Discarded lines with no settings", e.raw.joinToString(" / "))
            return
        }
        val author = e.author ?: sectionAuthor ?: run {
            issues += Issue(e.startLine, "Setup has no author; using 'Unknown'", e.raw.firstOrNull() ?: "")
            "Unknown"
        }
        val t = track ?: run {
            issues += Issue(e.startLine, "Setup is not under a track heading (dropped)", e.raw.joinToString(" / "))
            return
        }
        out += ParsedSetup(
            series = series,
            line = e.startLine,
            setup = Setup(
                track = t,
                author = author,
                tirePressure = e.tire,
                fenderFlare = e.fender,
                rearSpoiler = e.spoiler,
                frontSprings = e.front,
                rearSprings = e.rear,
                swayBar = e.sway,
                wedge = e.wedge,
                gears = e.gears,
                bestLap = e.bestLap,
                notes = e.notes.joinToString("; ").ifEmpty { null },
                raw = e.raw.joinToString("\n"),
            ),
        )
    }

    companion object {
        private fun isNa(lv: String) = lv == "n/a" || lv == "na"

        /** First number in the text; understands "73 1/2" and "3/4". */
        fun firstNumber(s: String): Double? {
            val m = Regex("""(\d+)\s+(\d)/(\d)|(\d+)/(\d+)|(\d*\.?\d+)""").find(s) ?: return null
            val g = m.groupValues
            return when {
                g[1].isNotEmpty() -> g[1].toDouble() + g[2].toDouble() / g[3].toDouble()
                g[4].isNotEmpty() -> g[4].toDouble() / g[5].toDouble()
                else -> g[6].toDouble()
            }
        }

        /** Normalises a track heading for alias lookup: "Watkin's Glen Setups:" -> "watkins glen". */
        fun normalizeTrackName(s: String): String =
            s.lowercase()
                .replace("'", "").replace("’", "")
                .replace('-', ' ')
                .trim().trimEnd(':').trim()
                .replace(Regex("""\s+setups?$"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
    }
}
