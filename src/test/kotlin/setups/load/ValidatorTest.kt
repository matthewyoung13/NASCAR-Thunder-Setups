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
        assertEquals(emptyList(), Validator.validate(data(SetupFile("t3", "cup", setups = listOf(setup().copy(swayBar = "3/4 in"))))))
    }

    @Test
    fun `unknown track`() {
        val errors = Validator.validate(data(SetupFile("t4", "cup", setups = listOf(setup(track = "nowhere")))))
        assertTrue(errors.single().contains("unknown track 'nowhere'"), errors.toString())
    }

    @Test
    fun `field the game does not have`() {
        val errors = Validator.validate(data(SetupFile("t4", "cup", setups = listOf(setup().copy(swayBar = "1 in")))))
        assertTrue(errors.single().contains("has no Sway Bar setting"), errors.toString())
    }

    @Test
    fun `values out of range`() {
        val bad = setup().copy(tirePressure = 45, gears = Gears(preset = 12, ratios = listOf(3.6, 2.1)))
        val errors = Validator.validate(data(SetupFile("t4", "cup", setups = listOf(bad))))
        assertEquals(3, errors.size, errors.toString())
    }

    @Test
    fun `duplicates and unknown series`() {
        val errors = Validator.validate(data(SetupFile("t4", "busch", setups = listOf(setup(), setup().copy(raw = "x")))))
        assertTrue(errors.any { "no series 'busch'" in it }, errors.toString())
        assertTrue(errors.any { "duplicate of t4/busch #1" in it }, errors.toString())
    }

    @Test
    fun `several files per game and series, duplicates caught across them`() {
        val main = SetupFile("t3", "cup", setups = listOf(setup()))
        val other = SetupFile("t3", "cup", source = "IGN", setups = listOf(setup(author = "B")))
        assertEquals(emptyList(), Validator.validate(data(main, other)))

        val copy = SetupFile("t3", "cup", source = "IGN", setups = listOf(setup()))
        val errors = Validator.validate(data(main, copy))
        assertTrue(errors.single().contains("t3/cup (IGN) #1 (A @ bristol): duplicate of t3/cup #1"), errors.toString())
    }

    @Test
    fun `file source fills in setups without their own`() {
        val file = SetupFile("t3", "cup", source = "IGN", setups = listOf(setup(), setup(author = "B").copy(source = "Forum")))
        assertEquals(listOf("IGN", "Forum"), file.resolvedSetups().map { it.source })
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
