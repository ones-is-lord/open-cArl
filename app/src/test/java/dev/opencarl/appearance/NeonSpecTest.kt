package dev.opencarl.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeonSpecTest {

    @Test
    fun `rainbow keeps the nine css stops in order`() {
        val stops = NeonSpec.rainbow
        assertEquals(9, stops.size)
        assertEquals(0xFFFF18C8, stops.first().argb)
        assertEquals(0xFFFF18C8, stops.last().argb)
        stops.zipWithNext { a, b ->
            assertTrue("stops must ascend: ${a.fraction} -> ${b.fraction}", b.fraction > a.fraction)
        }
        assertEquals(0f, stops.first().fraction, 0.0001f)
        assertEquals(1f, stops.last().fraction, 0.0001f)
    }

    @Test
    fun `phase is a sawtooth over the period`() {
        assertEquals(0f, NeonSpec.phaseAt(0), 0.0001f)
        assertEquals(0.5f, NeonSpec.phaseAt(6_000), 0.0001f)
        assertEquals(0f, NeonSpec.phaseAt(12_000), 0.0001f)
        assertEquals(0.25f, NeonSpec.phaseAt(15_000), 0.0001f)
        assertEquals(0.75f, NeonSpec.phaseAt(-3_000), 0.0001f)
        assertEquals(0f, NeonSpec.phaseAt(5_000, 0), 0.0001f)
    }

    @Test
    fun `fit shrinks wide text and never exceeds the clamp`() {
        assertEquals(20f, NeonSpec.fitFontSize(40f, 400f, 200f, 20f, 64f), 0.001f)
        assertEquals(40f, NeonSpec.fitFontSize(40f, 100f, 400f, 20f, 40f), 0.001f)
        assertEquals(20f, NeonSpec.fitFontSize(40f, 0f, 400f, 20f, 64f), 0.001f)
        assertEquals(20f, NeonSpec.fitFontSize(0f, 100f, 400f, 20f, 64f), 0.001f)
    }

    @Test
    fun `display text is uppercase and trimmed but never rewritten`() {
        assertEquals("OPEN CARL", NeonSpec.display("  open cArl  "))
        assertEquals("SKY'S THE LIMIT", NeonSpec.display("sky's the limit"))
        assertEquals("", NeonSpec.display("   "))
        assertEquals("mixed Case", NeonSpec.banner("  mixed Case ", uppercase = false))
        assertEquals("MIXED CASE", NeonSpec.banner("  mixed Case ", uppercase = true))
    }

    @Test
    fun `gradient span matches the css background size`() {
        assertEquals(2.2f, NeonSpec.GRADIENT_SPAN, 0.0001f)
        assertEquals(12_000L, NeonSpec.PERIOD_MS)
    }
}
