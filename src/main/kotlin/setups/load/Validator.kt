package setups.load

import setups.model.Field
import setups.model.Setup

/** Checks the data for mistakes. An empty list means the data is good. */
object Validator {

    fun validate(data: Dataset): List<String> {
        val errors = mutableListOf<String>()

        fun dupes(kind: String, ids: List<String>) =
            ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.forEach { errors += "Duplicate $kind id '$it'" }
        dupes("game", data.games.map { it.id })
        dupes("series", data.series.map { it.id })
        dupes("track", data.tracks.map { it.id })

        data.games.forEach { g ->
            g.series.filter { it !in data.seriesById }.forEach { errors += "Game '${g.id}' lists unknown series '$it'" }
        }

        val seenFiles = mutableSetOf<Pair<String, String>>()
        data.setupFiles.forEach { file ->
            val where = "${file.game}/${file.series}"
            if (!seenFiles.add(file.game to file.series)) errors += "$where: more than one file for this game and series"
            val game = data.gamesById[file.game]
            if (game == null) {
                errors += "$where: unknown game '${file.game}'"
                return@forEach
            }
            if (file.series !in game.series) errors += "$where: ${game.name} has no series '${file.series}' (add it to games.yaml)"

            val seen = mutableMapOf<Setup, Int>()
            file.setups.forEachIndexed { i, s ->
                val at = "$where #${i + 1} (${s.author} @ ${s.track})"
                if (s.track !in data.tracksById) errors += "$at: unknown track '${s.track}'"
                if (s.author.isBlank()) errors += "$at: missing author"
                if (s.usedFields().isEmpty()) errors += "$at: no settings at all"
                (s.usedFields() - game.fields.toSet()).forEach {
                    errors += "$at: ${game.name} has no ${it.label} setting"
                }
                errors += rangeErrors(s).map { "$at: $it" }
                seen.put(s.valuesKey(), i + 1)?.let { first -> errors += "$at: duplicate of #$first" }
            }
        }
        return errors
    }

    private fun rangeErrors(s: Setup): List<String> = buildList {
        fun check(field: Field, value: Number?, min: Double, max: Double) {
            if (value != null && value.toDouble() !in min..max) add("${field.label} $value is outside $min..$max")
        }
        check(Field.tirePressure, s.tirePressure, 15.0, 30.0)
        check(Field.fenderFlare, s.fenderFlare, 73.0, 76.0)
        check(Field.rearSpoiler, s.rearSpoiler, 45.0, 75.0)
        check(Field.frontSprings, s.frontSprings, 1.0, 100.0)
        check(Field.rearSprings, s.rearSprings, 1.0, 100.0)
        check(Field.wedge, s.wedge, -4.0, 4.0)
        s.swayBar?.let { if (!Regex("""^(\d+ \d/\d|\d/\d|\d+) in$""").matches(it)) add("Sway Bar '$it' should look like '3/4 in'") }
        s.gears?.let { g ->
            if (g.preset != null && g.preset !in 1..10) add("Gear preset ${g.preset} is outside 1..10")
            if (g.ratios != null && g.ratios.size != 4) add("Gear ratios must list exactly 4 gears (use null for 'default')")
            g.ratios?.filterNotNull()?.forEach { if (it !in 0.5..4.5) add("Gear ratio $it is outside 0.5..4.5") }
            g.rearEnd?.let { if (it !in 2.5..4.5) add("Rear end ratio $it is outside 2.5..4.5") }
        }
    }
}
