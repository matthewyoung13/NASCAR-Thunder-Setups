package setups.load

import setups.model.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.*

class ValidatorTest {

    private val game2003 = Game("t3", "Thunder 2003", 2002, listOf("cup"), Field.entries.toList())
    private val game2004 = Game("t4", "Thunder 2004", 2003, listOf("cup"), Field.entries - Field.swayBar)
    private val tracks = listOf(Track("bristol", "Bristol", TrackType.short))
    private val series = listOf(Series("cup", "Cup"))

    private fun data(vararg files: SetupFile) = Dataset(listOf(game2003, game2004), series, tracks, files.toList())
    private fun setup(author: String = "A", track: String = "bristol") =
        Setup(track = track, author = author, tirePressure = 20, wedge = 0.5)

    @Test
    fun `good data has no errors`() {
        assertEquals(emptyList(), Validator.validate(data(SetupFile("t3", "cup", listOf(setup().copy(swayBar = "3/4 in"))))))
    }

    @Test
    fun `unknown track`() {
        val errors = Validator.validate(data(SetupFile("t4", "cup", listOf(setup(track = "nowhere")))))
        assertTrue(errors.single().contains("unknown track 'nowhere'"), errors.toString())
    }

    @Test
    fun `field the game does not have`() {
        val errors = Validator.validate(data(SetupFile("t4", "cup", listOf(setup().copy(swayBar = "1 in")))))
        assertTrue(errors.single().contains("has no Sway Bar setting"), errors.toString())
    }

    @Test
    fun `values out of range`() {
        val bad = setup().copy(tirePressure = 45, gears = Gears(preset = 12, ratios = listOf(3.6, 2.1)))
        val errors = Validator.validate(data(SetupFile("t4", "cup", listOf(bad))))
        assertEquals(3, errors.size, errors.toString())
    }

    @Test
    fun `duplicates and unknown series`() {
        val errors = Validator.validate(data(SetupFile("t4", "busch", listOf(setup(), setup().copy(raw = "x")))))
        assertTrue(errors.any { "no series 'busch'" in it }, errors.toString())
        assertTrue(errors.any { "duplicate of #1" in it }, errors.toString())
    }

    /** The real dataset in data/ must always pass. */
    @Test
    fun `real data is valid`() {
        val root = Path(System.getProperty("project.root") ?: ".")
        val data = DataLoader.load(root / "data")
        assertEquals(emptyList(), Validator.validate(data))
        assertEquals(setOf("thunder-2003", "thunder-2004", "thunder-2005"), data.setupFiles.map { it.game }.toSet())
        assertTrue(data.setupFiles.sumOf { it.setups.size } >= 200)
    }
}
