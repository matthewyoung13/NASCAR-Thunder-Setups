package setups.render

import setups.model.Field
import setups.model.Gears
import setups.model.Setup

/** Turns setup values into display text. */
object Format {

    /** 73.0 -> "73", 73.5 -> "73.5" */
    fun num(d: Double): String =
        if (d == Math.floor(d)) d.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, d).trimEnd('0')

    fun ratio(d: Double) = "%.2f".format(java.util.Locale.ROOT, d)

    fun wedge(d: Double): String = when {
        d > 0 -> "+" + "%.1f".format(java.util.Locale.ROOT, d)
        d < 0 -> "−" + "%.1f".format(java.util.Locale.ROOT, -d)
        else -> "0.0"
    }

    private val ordinals = listOf("1st", "2nd", "3rd", "4th")

    fun gears(g: Gears): String {
        if (g.useDefault && g.preset == null && g.ratios == null && g.rearEnd == null) return "Default"
        val parts = mutableListOf<String>()
        g.preset?.let { parts += "Preset $it" }
        g.ratios?.let { r ->
            parts += if (r.all { it != null }) r.joinToString(" / ") { ratio(it!!) }
            else r.mapIndexedNotNull { i, v -> v?.let { "${ordinals[i]} ${ratio(it)}" } }.joinToString(", ")
        }
        g.rearEnd?.let { parts += "Rear end ${ratio(it)}" }
        return parts.joinToString(" · ")
    }

    /** Display value of one field, or null if the setup doesn't set it. */
    fun field(s: Setup, f: Field): String? = when (f) {
        Field.tirePressure -> s.tirePressure?.let { "$it psi" }
        Field.fenderFlare -> s.fenderFlare?.let { "${num(it)} in" }
        Field.rearSpoiler -> s.rearSpoiler?.let { "$it°" }
        Field.frontSprings -> s.frontSprings?.let { "$it%" }
        Field.rearSprings -> s.rearSprings?.let { "$it%" }
        Field.swayBar -> s.swayBar
        Field.wedge -> s.wedge?.let { "${wedge(it)} turns" }
        Field.gears -> s.gears?.let { gears(it) }
    }

    /** Plain text for the "copy setup" button. */
    fun copyText(trackName: String, gameName: String, s: Setup, fields: List<Field>): String = buildString {
        appendLine("$gameName — $trackName")
        appendLine("Setup by ${s.author}")
        fields.forEach { f -> field(s, f)?.let { appendLine("${f.label}: $it") } }
        s.bestLap?.let { appendLine("Best lap: $it") }
        s.notes?.let { appendLine("Notes: $it") }
    }.trimEnd()

    fun slug(s: String): String =
        s.lowercase().replace("'", "").replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "unknown" }
}
