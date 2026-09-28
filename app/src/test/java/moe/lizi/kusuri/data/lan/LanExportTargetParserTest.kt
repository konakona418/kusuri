package moe.lizi.kusuri.data.lan

import org.junit.Assert.assertEquals
import org.junit.Test

class LanExportTargetParserTest {

    private val localIp = "192.168.2.103"

    @Test
    fun `parses the qr uri with host port and code`() {
        val result = parseLanExportTarget("kusuri://lan-export/1?h=192.168.2.110&p=47821&c=K4F9M2", localIp)

        assertEquals(
            LanExportParse.Parsed(LanExportTarget(host = "192.168.2.110", port = 47821, code = "K4F9M2")),
            result,
        )
    }

    @Test
    fun `qr uri without a port falls back to the default`() {
        val result = parseLanExportTarget("kusuri://lan-export/1?h=10.0.0.7&c=K4F9M2", localIp)

        assertEquals(47821, (result as LanExportParse.Parsed).target.port)
    }

    @Test
    fun `qr uri without a version is still accepted`() {
        val result = parseLanExportTarget("kusuri://lan-export?h=10.0.0.7&c=K4F9M2", localIp)

        assertEquals(LanExportParse.Parsed(LanExportTarget("10.0.0.7", 47821, "K4F9M2")), result)
    }

    @Test
    fun `a qr from a newer version is refused as unsupported`() {
        val result = parseLanExportTarget("kusuri://lan-export/2?h=10.0.0.7&c=K4F9M2", localIp)

        assertEquals(LanExportParse.UnsupportedVersion, result)
    }

    @Test
    fun `some other qr code is not ours`() {
        assertEquals(LanExportParse.NotKusuri, parseLanExportTarget("https://example.com/", localIp))
        assertEquals(LanExportParse.NotKusuri, parseLanExportTarget("kusuri://other-thing/1?h=1.2.3.4", localIp))
    }

    @Test
    fun `parses the full hand typed form`() {
        val result = parseLanExportTarget("192.168.2.110:47821#K4F9M2", localIp)

        assertEquals(LanExportParse.Parsed(LanExportTarget("192.168.2.110", 47821, "K4F9M2")), result)
    }

    @Test
    fun `hand typed form may omit the port`() {
        val result = parseLanExportTarget("  192.168.2.110#K4F9M2  ", localIp)

        assertEquals(LanExportParse.Parsed(LanExportTarget("192.168.2.110", 47821, "K4F9M2")), result)
    }

    @Test
    fun `short form is completed from this device's subnet`() {
        val result = parseLanExportTarget("110#K4F9M2", localIp)

        assertEquals(LanExportParse.Parsed(LanExportTarget("192.168.2.110", 47821, "K4F9M2")), result)
    }

    @Test
    fun `short form without a local address asks for the full form instead of guessing`() {
        assertEquals(LanExportParse.NeedsLocalAddress, parseLanExportTarget("110#K4F9M2", null))
        assertEquals(LanExportParse.NeedsLocalAddress, parseLanExportTarget("110#K4F9M2", "not-an-ip"))
    }

    @Test
    fun `codes survive lower case separators and confusable characters`() {
        // 手输时光标最容易看错的地方:L 当 1、O 当 0,以及顺手打的分隔符。
        assertEquals("K4F9M2", normalizeLanExportCode("k4f9-m2"))
        assertEquals("012345", normalizeLanExportCode("Ol2345"))
        assertEquals("11F9M2", normalizeLanExportCode("ILF9M2"))

        assertEquals(
            LanExportParse.Parsed(LanExportTarget("192.168.2.110", 47821, "K4F9M2")),
            parseLanExportTarget("192.168.2.110:47821#k4f9m2", localIp),
        )
    }

    @Test
    fun `rejects malformed targets`() {
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("", localIp))
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110", localIp)) // 没带口令
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110#", localIp)) // 空口令
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110#K4F9M", localIp)) // 口令少一位
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110#K4F9M2X", localIp)) // 口令多一位
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110#K4F9M!", localIp)) // 字母表外
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110:0#K4F9M2", localIp)) // 端口越界
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110:70000#K4F9M2", localIp))
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110:abc#K4F9M2", localIp))
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("999.168.2.110#K4F9M2", localIp)) // 不是 IPv4
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.002.110#K4F9M2", localIp)) // 前导零
        assertEquals(LanExportParse.Invalid, parseLanExportTarget("192.168.2.110.5#K4F9M2", localIp))
    }
}
