package setups.model

import kotlinx.serialization.Serializable

/** A setup parameter a game exposes in its garage screen. */
@Serializable
enum class Field(val label: String, val unit: String) {
    tirePressure("Tire Pressure", "psi"),
    fenderFlare("Fender Flare", "in"),
    rearSpoiler("Rear Spoiler", "°"),
    frontSprings("Front Springs", "%"),
    rearSprings("Rear Springs", "%"),
    swayBar("Sway Bar", "in"),
    wedge("Wedge", "turns"),
    gears("Gears", ""),
}

@Serializable
data class Game(
    val id: String,
    val name: String,
    val released: Int,
    val series: List<String>,
    val fields: List<Field>,
    val description: String? = null,
)

@Serializable
data class Series(
    val id: String,
    val name: String,
    val description: String? = null,
)

@Serializable
enum class TrackType(val label: String) {
    superspeedway("Superspeedways"),
    intermediate("Intermediate ovals"),
    short("Short tracks"),
    road("Road courses"),
    infield("Infield road courses"),
    fantasy("Fantasy tracks"),
}

@Serializable
data class Track(
    val id: String,
    val name: String,
    val type: TrackType,
    val location: String? = null,
    val aliases: List<String> = emptyList(),
)

/**
 * Gear settings. Usually either a [preset] or full [ratios], but sources mix them:
 * "Preset 3, but set 4th to 1.37" is preset = 3, ratios = [null, null, null, 1.37].
 * A null ratio means "leave at the default / preset value".
 */
@Serializable
data class Gears(
    val preset: Int? = null,
    val ratios: List<Double?>? = null,
    val rearEnd: Double? = null,
    val useDefault: Boolean = false,
)

@Serializable
data class Setup(
    val track: String,
    val author: String,
    val tirePressure: Int? = null,
    val fenderFlare: Double? = null,
    val rearSpoiler: Int? = null,
    val frontSprings: Int? = null,
    val rearSprings: Int? = null,
    val swayBar: String? = null,
    val wedge: Double? = null,
    val gears: Gears? = null,
    val bestLap: String? = null,
    val notes: String? = null,
    val source: String? = null,
    /** The setup exactly as it appeared in the source text, for checking. */
    val raw: String? = null,
) {
    /** Which fields this setup actually sets. */
    fun usedFields(): Set<Field> = buildSet {
        if (tirePressure != null) add(Field.tirePressure)
        if (fenderFlare != null) add(Field.fenderFlare)
        if (rearSpoiler != null) add(Field.rearSpoiler)
        if (frontSprings != null) add(Field.frontSprings)
        if (rearSprings != null) add(Field.rearSprings)
        if (swayBar != null) add(Field.swayBar)
        if (wedge != null) add(Field.wedge)
        if (gears != null) add(Field.gears)
    }

    /** The setup's values without provenance; two setups with equal keys are duplicates. */
    fun valuesKey() = copy(raw = null, source = null, notes = null, author = author.lowercase())
}

/** One YAML file under data/setups: all setups for one game + series. */
@Serializable
data class SetupFile(
    val game: String,
    val series: String,
    val setups: List<Setup>,
)

@Serializable
data class GamesFile(val games: List<Game>)

@Serializable
data class SeriesFile(val series: List<Series>)

@Serializable
data class TracksFile(val tracks: List<Track>)
