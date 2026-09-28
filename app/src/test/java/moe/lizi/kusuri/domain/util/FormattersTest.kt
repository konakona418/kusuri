package moe.lizi.kusuri.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `amounts drop trailing zeros`() {
        assertEquals("1", formatAmount(1.0))
        assertEquals("0", formatAmount(0.0))
        assertEquals("0.5", formatAmount(0.5))
        assertEquals("12.25", formatAmount(12.25))
        assertEquals("30", formatAmount(30.0))
    }
}
