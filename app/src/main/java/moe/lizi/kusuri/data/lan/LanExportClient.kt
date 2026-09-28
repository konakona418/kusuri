package moe.lizi.kusuri.data.lan

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000
private const val DEFAULT_READ_TIMEOUT_MS = 15_000

/** 应答必须很小;给一个上限,免得对面(或中间人)一直灌数据。 */
private const val MAX_RESPONSE_BYTES = 64 * 1024

private const val MAX_FILE_NAME_LENGTH = 120

private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm")
private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 建议的文件名(脚本可改名;契约 §3.1)。 */
fun lanExportBackupFileName(at: Instant, zone: ZoneId): String =
    "kusuri-backup-${TIMESTAMP.format(at.atZone(zone))}.json"

/** CSV 的建议文件名:带上区间,便于电脑上区分不同时期导出的文件。 */
fun lanExportRecordsFileName(from: Instant, to: Instant, zone: ZoneId): String {
    val start = if (from == Instant.EPOCH) "all" else DATE.format(from.atZone(zone))
    return "kusuri-records-$start-${DATE.format(to.atZone(zone))}.csv"
}

/**
 * 把一份导出内容推送到电脑上的接收脚本(docs/lan-export.md §3)。
 *
 * 手写裸 socket 而不是 `HttpURLConnection`:不触发 Android 9+ 的明文策略(manifest 不必加
 * `usesCleartextTraffic`),也绕过系统代理,保证真的直连局域网(契约 §6)。
 *
 * 请求构造与应答解析都是纯函数,单测里可以和本地 `ServerSocket` 对打。
 */
class LanExportClient(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) {

    /**
     * 把 [content] 作为一次推送发出去。**不做重试**:重试可能在电脑上留下重复文件。
     *
     * 返回 [LanExportResult.Delivered] 时,[LanExportResult.Delivered.sha256] 已经与发送字节核对过。
     */
    suspend fun send(
        target: LanExportTarget,
        kind: LanExportKind,
        fileName: String,
        content: ByteArray,
    ): LanExportResult = withContext(Dispatchers.IO) {
        val request = buildLanExportRequest(target, kind, fileName, content)
        val raw = try {
            exchange(target, request)
        } catch (failure: LanExportTransportException) {
            return@withContext LanExportResult.Failed(failure.failure)
        }
        parseLanExportResponse(raw, sha256Hex(content))
    }

    private fun exchange(target: LanExportTarget, request: ByteArray): ByteArray {
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(target.host, target.port), connectTimeoutMs)
            } catch (timeout: SocketTimeoutException) {
                throw LanExportTransportException(LanExportFailure.Timeout(SendStage.CONNECT))
            } catch (error: IOException) {
                throw LanExportTransportException(
                    LanExportFailure.Unreachable(error.reachability(), error.describe()),
                )
            }
            socket.soTimeout = readTimeoutMs
            try {
                socket.getOutputStream().apply {
                    write(request)
                    flush()
                }
                return readResponse(socket.getInputStream())
            } catch (timeout: SocketTimeoutException) {
                throw LanExportTransportException(LanExportFailure.Timeout(SendStage.READ))
            } catch (error: IOException) {
                throw LanExportTransportException(
                    LanExportFailure.Unreachable(error.reachability(), error.describe()),
                )
            }
        }
    }

    private fun readResponse(input: InputStream): ByteArray {
        val sink = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            sink.write(chunk, 0, read)
            if (sink.size() > MAX_RESPONSE_BYTES) {
                throw LanExportTransportException(LanExportFailure.BadResponse)
            }
        }
        return sink.toByteArray()
    }
}

/** 请求字节 = 头 + 原样的文件字节(契约 §3.1)。 */
internal fun buildLanExportRequest(
    target: LanExportTarget,
    kind: LanExportKind,
    fileName: String,
    content: ByteArray,
): ByteArray {
    val head = buildString {
        append("POST /upload HTTP/1.1\r\n")
        append("Host: ").append(target.host).append(':').append(target.port).append("\r\n")
        append("X-Kusuri-Code: ").append(target.code).append("\r\n")
        append("X-Kusuri-Kind: ").append(kind.headerValue).append("\r\n")
        append("X-Kusuri-Filename: ").append(sanitizeLanExportFileName(fileName)).append("\r\n")
        append("Content-Type: ").append(kind.contentType).append("\r\n")
        append("Content-Length: ").append(content.size).append("\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }
    return head.toByteArray(Charsets.US_ASCII) + content
}

/**
 * 解析应答(契约 §3.2):
 *
 * - `200`:要求带 `sha256`,且与 [expectedSha256] 一致,否则按失败处理——"电脑说成功了但内容不符"
 *   比"失败"更危险,不能当成成功;
 * - `401` / `413` 单独分类,界面给不同的下一步;其余 4xx/5xx 归 [LanExportFailure.Rejected] 并带状态码。
 */
internal fun parseLanExportResponse(raw: ByteArray, expectedSha256: String): LanExportResult {
    val text = raw.toString(Charsets.UTF_8)
    val separator = text.indexOf("\r\n\r\n")
    if (separator < 0) return failed(LanExportFailure.BadResponse)

    val head = text.substring(0, separator)
    val body = text.substring(separator + 4).trim()
    val status = head.lineSequence()
        .firstOrNull()
        ?.trim()
        ?.split(' ')
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: return failed(LanExportFailure.BadResponse)

    return when (status) {
        200 -> parseDelivered(body, expectedSha256)
        401 -> failed(LanExportFailure.BadCode)
        413 -> failed(LanExportFailure.TooLarge)
        else -> failed(LanExportFailure.Rejected(status, parseErrorField(body)))
    }
}

/** 发送字节的 sha256(十六进制小写),与脚本落盘后打印的值可逐字对照。 */
internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** 文件名只留可打印 ASCII:协议头里一个换行就能注入额外请求头。 */
internal fun sanitizeLanExportFileName(name: String): String {
    val cleaned = buildString {
        name.forEach { character ->
            append(if (character.code in 0x21..0x7E && character !in "\"\\/:*?<>|") character else '_')
        }
    }.trim('.', ' ', '_').take(MAX_FILE_NAME_LENGTH)
    return cleaned.ifEmpty { "kusuri-export" }
}

private fun parseDelivered(body: String, expectedSha256: String): LanExportResult {
    val json = runCatching { JSONObject(body) }.getOrNull()
        ?: return failed(LanExportFailure.BadResponse)
    val saved = json.optString("saved").takeIf { it.isNotBlank() }
        ?: return failed(LanExportFailure.BadResponse)
    val sha256 = json.optString("sha256").takeIf { it.isNotBlank() }
        ?: return failed(LanExportFailure.BadResponse)
    if (!sha256.equals(expectedSha256, ignoreCase = true)) {
        return failed(LanExportFailure.IntegrityMismatch(expected = expectedSha256, actual = sha256.lowercase()))
    }
    return LanExportResult.Delivered(
        savedPath = saved,
        bytes = json.optLong("bytes", -1L),
        sha256 = sha256.lowercase(),
    )
}

private fun parseErrorField(body: String): String? = runCatching {
    JSONObject(body).optString("error").takeIf { it.isNotBlank() }
}.getOrNull()

private fun failed(failure: LanExportFailure): LanExportResult = LanExportResult.Failed(failure)

private fun IOException.reachability(): Reachability = when (this) {
    is NoRouteToHostException -> Reachability.NO_ROUTE
    is ConnectException -> Reachability.REFUSED
    is UnknownHostException -> Reachability.UNKNOWN_HOST
    is SocketException -> Reachability.DROPPED
    else -> Reachability.OTHER
}

private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "unknown")

/** 把一个失败原因从 socket 层带到 [LanExportClient.send] 的返回值里。 */
private class LanExportTransportException(val failure: LanExportFailure) : Exception()
