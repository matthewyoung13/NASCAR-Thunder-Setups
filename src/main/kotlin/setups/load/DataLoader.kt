package setups.load

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import setups.model.*
import java.nio.file.Path
import kotlin.io.path.*

/** Everything under data/, loaded and indexed. */
class Dataset(
    val games: List<Game>,
    val series: List<Series>,
    val tracks: List<Track>,
    val setupFiles: List<SetupFile>,
) {
    val gamesById = games.associateBy { it.id }
    val seriesById = series.associateBy { it.id }
    val tracksById = tracks.associateBy { it.id }

    fun setupsFor(game: String, series: String): List<Setup> =
        setupFiles.filter { it.game == game && it.series == series }.flatMap { it.setups }
}

object DataLoader {
    val yaml = Yaml(configuration = YamlConfiguration(strictMode = true))

    @OptIn(ExperimentalPathApi::class)
    fun load(dataDir: Path): Dataset {
        val games = read(dataDir / "games.yaml", GamesFile.serializer()).games
        val series = read(dataDir / "series.yaml", SeriesFile.serializer()).series
        val tracks = read(dataDir / "tracks.yaml", TracksFile.serializer()).tracks
        val setupsDir = dataDir / "setups"
        val setupFiles = if (setupsDir.exists()) {
            setupsDir.walk()
                .filter { it.isRegularFile() && it.extension in setOf("yaml", "yml") }
                .sortedBy { it.toString() }
                .map { read(it, SetupFile.serializer()) }
                .toList()
        } else emptyList()
        return Dataset(games, series, tracks, setupFiles)
    }

    private fun <T> read(file: Path, serializer: KSerializer<T>): T =
        try {
            yaml.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not read $file: ${e.message}", e)
        }
}
