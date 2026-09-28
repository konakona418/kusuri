package moe.lizi.kusuri.data.lan

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.time.ZoneOffset
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 请求构造与应答解析都是纯函数;传输那层用一个本地 [ServerSocket] 当假接收端对打,
 * 所以这条链路不需要真机也不需要电脑就能验(docs/plan.md §13 M7.2)。
 */
@RunWith(RobolectricTestRunner::class)
class LanExportClientTest {

    private val target = LanExportTarget(host = "127.0.0.1", port = 1, code = "K4F9M2")
    private val content = """{"formatVersion":2}""".toByteArray()

    // --- 纯逻辑 ---

    @Test
    fun `request carries the contract headers and the raw bytes`() {
        val request = buildLanExportRequest(
            target = target.copy(port = 47821),
            kind = LanExportKind.JSON,
            fileName = "kusuri-backup-2026-09-28T1432.json",
            content = content,
        )
        val head = request.toString(Charsets.US_ASCII).substringBefore("\r\n\r\n")

        assertTrue(head.startsWith("POST /upload HTTP/1.1\r\n"))
        assertTrue(head.contains("\r\nHost: 127.0.0.1:47821\r\n"))
        assertTrue(head.contains("\r\nX-Kusuri-Code: K4F9M2\r\n"))
        assertTrue(head.contains("\r\nX-Kusuri-Kind: json\r\n"))
        assertTrue(head.contains("\r\nX-Kusuri-Filename: kusuri-backup-2026-09-28T1432.json\r\n"))
        assertTrue(head.contains("\r\nContent-Type: application/json; charset=utf-8\r\n"))
        assertTrue(head.contains("\r\nContent-Length: ${content.size}\r\n"))
        assertTrue(head.endsWith("\r\nConnection: close"))
        assertTrue(request.copyOfRange(request.size - content.size, request.size).contentEquals(content))
    }

    @Test
    fun `file names cannot inject extra headers`() {
        val request = buildLanExportRequest(
            target = target,
            kind = LanExportKind.RECORDS,
            fileName = "evil\r\nX-Kusuri-Code: 000000\r\n\r\n",
            content = content,
        )
        val head = request.toString(Charsets.US_ASCII).substringBefore("\r\n\r\n")

        assertEquals(1, Regex("X-Kusuri-Code:").findAll(head).count())
    }

    @Test
    fun `sha256 matches known vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun `file names carry the range`() {
        val at = Instant.parse("2026-09-28T14:32:00Z")
        assertEquals("kusuri-backup-2026-09-28T1432.json", lanExportBackupFileName(at, ZoneOffset.UTC))
        assertEquals(
            "kusuri-records-2026-08-29-2026-09-28.csv",
            lanExportRecordsFileName(at.minusSeconds(30 * 24 * 3600), at, ZoneOffset.UTC),
        )
        assertEquals(
            "kusuri-records-all-2026-09-28.csv",
            lanExportRecordsFileName(Instant.EPOCH, at, ZoneOffset.UTC),
        )
    }

    @Test
    fun `a delivered response is only delivered when the digest matches`() {
        val digest = sha256Hex(content)

        assertEquals(
            LanExportResult.Delivered("/home/lain/Downloads/kusuri-backup.json", content.size.toLong(), digest),
            parseLanExportResponse(
                """HTTP/1.1 200 OK
Content-Length: 1

{"ok":true,"saved":"/home/lain/Downloads/kusuri-backup.json","sha256":"$digest","bytes":${content.size}}"""
                    .replace("\n", "\r\n").toByteArray(),
                digest,
            ),
        )

        // 电脑说成功了,但内容对不上:必须当失败,不能当成功。
        assertEquals(
            LanExportResult.Failed(LanExportFailure.IntegrityMismatch(expected = digest, actual = "00ff")),
            parseLanExportResponse(
                """HTTP/1.1 200 OK

{"ok":true,"saved":"/tmp/x.json","sha256":"00ff","bytes":1}""".replace("\n", "\r\n").toByteArray(),
                digest,
            ),
        )

        // 少了校验值说明对面不是我们的脚本。
        assertEquals(
            LanExportResult.Failed(LanExportFailure.BadResponse),
            parseLanExportResponse(
                """HTTP/1.1 200 OK

{"ok":true,"saved":"/tmp/x.json"}""".replace("\n", "\r\n").toByteArray(),
                digest,
            ),
        )
    }

    @Test
    fun `each refusal keeps its own identity`() {
        assertEquals(
            LanExportResult.Failed(LanExportFailure.BadCode),
            parseLanExportResponse("HTTP/1.1 401 Unauthorized\r\n\r\n{\"ok\":false,\"error\":\"bad_code\"}".toByteArray(), ""),
        )
        assertEquals(
            LanExportResult.Failed(LanExportFailure.TooLarge),
            parseLanExportResponse("HTTP/1.1 413 Content Too Large\r\n\r\n{\"ok\":false,\"error\":\"too_large\"}".toByteArray(), ""),
        )
        assertEquals(
            LanExportResult.Failed(LanExportFailure.Rejected(422, "bad_payload")),
            parseLanExportResponse("HTTP/1.1 422 Unprocessable\r\n\r\n{\"ok\":false,\"error\":\"bad_payload\"}".toByteArray(), ""),
        )
        assertEquals(
            LanExportResult.Failed(LanExportFailure.BadResponse),
            parseLanExportResponse("不是 HTTP,是别的什么".toByteArray(), ""),
        )
    }

    // --- 真 socket 对打 ---

    @Test
    fun `delivers over a real socket`() = runTest {
        val receiver = FakeReceiver { _, body -> okResponse(sha256Hex(body), "/tmp/kusuri-backup.json", body.size) }

        val result = client().send(target.copy(port = receiver.port), LanExportKind.JSON, "kusuri-backup.json", content)
        receiver.await()
        assertEquals(
            LanExportResult.Delivered("/tmp/kusuri-backup.json", content.size.toLong(), sha256Hex(content)),
            result,
        )
        assertEquals(content.toList(), receiver.body.toList())
        assertTrue(receiver.head.startsWith("POST /upload HTTP/1.1"))
    }

    @Test
    fun `surfaces a wrong code as its own failure`() = runTest {
        val receiver = FakeReceiver { _, _ -> "HTTP/1.1 401 Unauthorized\r\n\r\n{\"ok\":false,\"error\":\"bad_code\"}" }

        val result = client().send(target.copy(port = receiver.port), LanExportKind.JSON, "x.json", content)

        receiver.await()
        assertEquals(LanExportResult.Failed(LanExportFailure.BadCode), result)
    }

    @Test
    fun `nothing listening means unreachable, not a crash`() = runTest {
        val deadPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

        val result = client().send(target.copy(port = deadPort), LanExportKind.JSON, "x.json", content)

        assertTrue("应当是可定位的失败而不是抛异常", result is LanExportResult.Failed)
        val failure = (result as LanExportResult.Failed).failure
        assertEquals(Reachability.REFUSED, (failure as LanExportFailure.Unreachable).kind)
    }

    private fun client() = LanExportClient(connectTimeoutMs = 3_000, readTimeoutMs = 3_000)

    private fun okResponse(digest: String, path: String, bytes: Int) =
        """HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 1

{"ok":true,"saved":"$path","sha256":"$digest","bytes":$bytes}""".replace("\n", "\r\n")
}

/** 单次应答的假接收端:读一个请求,按 [respondWith] 回一个响应,然后收摊。 */
private class FakeReceiver(private val respondWith: (head: String, body: ByteArray) -> String) {

    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val worker = thread(isDaemon = true, name = "fake-receiver") {
        runCatching {
            server.accept().use { socket ->
                val (head, body) = readRequest(socket.getInputStream())
                this.head = head
                this.body = body
                socket.getOutputStream().apply {
                    write(respondWith(head, body).toByteArray())
                    flush()
                }
            }
        }
        runCatching { server.close() }
    }

    @Volatile
    var head: String = ""

    @Volatile
    var body: ByteArray = ByteArray(0)

    val port: Int get() = server.localPort

    fun await() {
        worker.join(5_000)
    }
}

private fun readRequest(input: InputStream): Pair<String, ByteArray> {
    val sink = ByteArrayOutputStream()
    while (true) {
        val next = input.read()
        if (next < 0) break
        sink.write(next)
        val bytes = sink.toByteArray()
        if (bytes.size >= 4 && bytes.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
    }
    val head = sink.toString(Charsets.US_ASCII.name())
    val length = head.lineSequence()
        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()
        ?: 0
    val body = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = input.read(body, offset, length - offset)
        if (read < 0) break
        offset += read
    }
    return head to body
}
