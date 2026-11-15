package setups.importer

import setups.model.Gears
import kotlin.test.*

class TxtParserTest {

    private val tracks = mapOf("bristol" to "bristol", "atlanta" to "atlanta", "watkins glen" to "watkins-glen", "lowes motor speedway (charlotte)" to "charlotte")
    private fun parse(text: String) = TxtParser { tracks[TxtParser.normalizeTrackName(it)] }.parse(text.trimIndent())

    @Test
    fun `author after each setup`() {
        val r = parse(
            """
            Bristol Setups
            Tire Pressure: 20
            Fender Flare: 76 in.
            Rear Spoiler: 70 degrees
            Front Springs: 100%
            Rear Springs: 100%
            Wedge: 0.0
            Gears: 1.) 3.10  2.) 2.30  3.) 1.80  4.) 1.38  Rear End.) 3.80
            Setup by: dan312791

            Tire Pressure: 19
            Wedge: +0.5
            Setup by: ZebFAN06
            """
        )
        assertEquals(2, r.setups.size)
        val a = r.setups[0].setup
        assertEquals("bristol", a.track)
        assertEquals("dan312791", a.author)
        assertEquals(20, a.tirePressure)
        assertEquals(76.0, a.fenderFlare)
        assertEquals(70, a.rearSpoiler)
        assertEquals(Gears(ratios = listOf(3.10, 2.30, 1.80, 1.38), rearEnd = 3.80), a.gears)
        assertEquals("ZebFAN06", r.setups[1].setup.author)
        assertEquals(0.5, r.setups[1].setup.wedge)
    }

    @Test
    fun `author heading a section`() {
        val r = parse(
            """
            Setups by Warhawk
            Atlanta
            Tire Pressure: 28
            Gears: Preset 9
            Bristol
            Tire Pressure: 20
            Gears: Preset: 10
            """
        )
        assertEquals(listOf("atlanta", "bristol"), r.setups.map { it.setup.track })
        assertTrue(r.setups.all { it.setup.author == "Warhawk" })
        assertEquals(listOf(9, 10), r.setups.map { it.setup.gears?.preset })
    }

    @Test
    fun `possessive section heading and track aliases`() {
        val r = parse(
            """
            Chadlyroush's Setups
            Lowes Motor Speedway (Charlotte) Setups
            Tire Pressure: 30
            Watkin's Glen
            Tire Pressure: 15
            """
        )
        assertEquals(listOf("charlotte", "watkins-glen"), r.setups.map { it.setup.track })
        assertEquals("Chadlyroush", r.setups[0].setup.author)
    }

    @Test
    fun `dash separators and odd values`() {
        val r = parse(
            """
            Atlanta
            Tire Pressure - 18 psi
            Fender Flare - 73 1/2 in.
            Front Sprigns: 44%
            Rear Suspension: 60%
            Sway Bar: 1 1/4 in.
            Wedge: -.0.5
            Gears: 1.) 3.60 2.) 2.00 3.) 1.40 4.) 0.97 Rear End.) Use default
            Setup by: Daryl
            """
        )
        val s = r.setups.single().setup
        assertEquals(18, s.tirePressure)
        assertEquals(73.5, s.fenderFlare)
        assertEquals(44, s.frontSprings)
        assertEquals(60, s.rearSprings)
        assertEquals("1 1/4 in", s.swayBar)
        assertEquals(-0.5, s.wedge)
        assertEquals(Gears(ratios = listOf(3.60, 2.00, 1.40, 0.97)), s.gears)
        assertTrue(r.issues.any { "typo" in it.message })
    }

    @Test
    fun `ambiguous dash wedge is flagged`() {
        val r = parse("Atlanta\nWedge - 1.5\nSetup by: Daryl")
        assertEquals(1.5, r.setups.single().setup.wedge)
        assertTrue(r.issues.any { "may mean -N" in it.message })
    }

    @Test
    fun `wg label with dash and negative value`() {
        val r = parse("Atlanta\nWG- -0.5\nSetup by: BTW66")
        assertEquals(-0.5, r.setups.single().setup.wedge)
    }

    @Test
    fun `partial gear changes`() {
        val r = parse(
            """
            Setups by X
            Atlanta
            Tire Pressure: 15
            Gears: preset 5; 4th 1.11
            Bristol
            Tire Pressure: 15
            Gear Preset: 3
            Gears: 1.)  Default  2.) Default  3.) Default  4.) 1.37  Rear End.) Default
            """
        )
        assertEquals(Gears(preset = 5, ratios = listOf(null, null, null, 1.11)), r.setups[0].setup.gears)
        assertEquals(Gears(preset = 3, ratios = listOf(null, null, null, 1.37)), r.setups[1].setup.gears)
    }

    @Test
    fun `gear and rear end on separate lines`() {
        val r = parse("Atlanta\nGear Preset: 4\nGear 4.) 0.98\nRear End.) 3.70\nSetup by: Warhawk")
        assertEquals(Gears(preset = 4, ratios = listOf(null, null, null, 0.98), rearEnd = 3.70), r.setups.single().setup.gears)
    }

    @Test
    fun `short gear notation and default gears`() {
        val r = parse(
            """
            Setups by M
            Atlanta
            15 PSI
            Front: 95
            Rear: 100
            Gears: 1) 3.6 2) 2.25 3) 1.37 4) 1.02 RE) 3.5
            Best Lap: 29.262
            Bristol
            Tire Pressure: 15
            Gears: Use default gear selection
            """
        )
        val a = r.setups[0].setup
        assertEquals(15, a.tirePressure)
        assertEquals(95, a.frontSprings)
        assertEquals(Gears(ratios = listOf(3.6, 2.25, 1.37, 1.02), rearEnd = 3.5), a.gears)
        assertEquals("29.262", a.bestLap)
        assertEquals(Gears(useDefault = true), r.setups[1].setup.gears)
    }

    @Test
    fun `n-a fender and spoiler are left empty`() {
        val s = parse("Atlanta\nFender Flare: N/A\nRear Spoiler: N/A\nTire Pressure: 30\nSetup by: A").setups.single().setup
        assertNull(s.fenderFlare)
        assertNull(s.rearSpoiler)
        assertEquals(30, s.tirePressure)
    }

    @Test
    fun `repeated spoiler with percent is rear springs`() {
        val r = parse("Atlanta\nRear Spoiler: 70 degrees\nFront Springs: 50%\nRear Spoiler: 75%\nSetup by: A")
        val s = r.setups.single().setup
        assertEquals(70, s.rearSpoiler)
        assertEquals(75, s.rearSprings)
    }

    @Test
    fun `garbled values are flagged`() {
        val r = parse("Watkins Glen\nTire Pressure: in.\nFront Springs: 60%15\nSetup by: A")
        val messages = r.issues.map { it.message }
        assertTrue(messages.any { "no number" in it })
        assertTrue(messages.any { "Unusual spring" in it })
    }

    @Test
    fun `noise lines are ignored`() {
        val r = parse("nextelcuplogosmall.jpg\nAdd your content here\nAtlanta\nTire Pressure: 15\nSetup by: A")
        assertEquals(1, r.setups.size)
        assertTrue(r.issues.isEmpty(), r.issues.toString())
    }

    @Test
    fun `combined down force and suspension lines`() {
        val r = parse(
            """
            Bristol - Food City 500/Sharpie 500

            Tire Pressure: 15psi
            Down Force: FF 74 1/2in; RS 70°
            Suspension: FS 40%; RS 60%; SB 1/2in; W -1.5
            Gear Ratio: 1st 3.60; 2nd 2.40; 3rd 1.80; 4th 1.40; RE 3.55
            Atlanta - MBNA America 500/NAPA 500

            Tire Pressure: 30psi
            Down Force: N/A
            Suspension: FS 63%; RS 68%; SB 7/8in; W +1.0
            """
        )
        assertEquals(listOf("bristol", "atlanta"), r.setups.map { it.setup.track })
        val s = r.setups[0].setup
        assertEquals(15, s.tirePressure)
        assertEquals(74.5, s.fenderFlare)
        assertEquals(70, s.rearSpoiler)
        assertEquals(40, s.frontSprings)
        assertEquals(60, s.rearSprings)
        assertEquals("1/2 in", s.swayBar)
        assertEquals(-1.5, s.wedge)
        assertEquals(Gears(ratios = listOf(3.60, 2.40, 1.80, 1.40), rearEnd = 3.55), s.gears)
        val plate = r.setups[1].setup
        assertNull(plate.fenderFlare)
        assertNull(plate.rearSpoiler)
        assertEquals(1.0, plate.wedge)
        assertTrue(r.issues.none { "may mean" in it.message || "Unusual" in it.message || "Unrecognised" in it.message }, r.issues.toString())
    }

    @Test
    fun `first number understands fractions`() {
        assertEquals(73.5, TxtParser.firstNumber("73 1/2 in."))
        assertEquals(0.75, TxtParser.firstNumber("3/4 in"))
        assertEquals(0.99, TxtParser.firstNumber(".99"))
    }
}
