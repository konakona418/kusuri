package moe.lizi.kusuri.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `header and rows use crlf and comma separators`() {
        val csv = toCsv(
            header = listOf("药物", "数量"),
            rows = listOf(listOf("二甲双胍", "1 粒"), listOf("布洛芬", "0.5 粒")),
        )

        assertEquals(
            "药物,数量\r\n二甲双胍,1 粒\r\n布洛芬,0.5 粒\r\n",
            csv,
        )
    }

    @Test
    fun `values with commas quotes or newlines are quoted and escaped`() {
        val csv = toCsv(
            header = listOf("a"),
            rows = listOf(listOf("x,y"), listOf("he said \"hi\""), listOf("line\nbreak")),
        )

        assertEquals(
            "a\r\n\"x,y\"\r\n\"he said \"\"hi\"\"\"\r\n\"line\nbreak\"\r\n",
            csv,
        )
    }

    @Test
    fun `plain values are not quoted`() {
        assertEquals("plain", escapeCsv("plain"))
    }
}
