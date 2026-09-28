package moe.lizi.kusuri.data.lan

import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** 脚本的默认端口,与契约一致(docs/lan-export.md §2)。 */
const val LAN_EXPORT_DEFAULT_PORT = 47821

/** Crockford base32:没有 I / L / O / U,手输时不易看错。 */
private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
private const val CODE_LENGTH = 6
private const val SCHEME = "kusuri"
private const val PURPOSE = "lan-export"
private const val SUPPORTED_VERSION = 1

/**
 * 解析"往哪发":扫码内容与手输内容走同一个入口(docs/lan-export.md §4)。
 *
 * 认这三种写法:
 *
 * - 二维码:`kusuri://lan-export/1?h=192.168.2.110&p=47821&c=K4F9M2`
 * - 完整式:`192.168.2.110:47821#K4F9M2`(端口可省,默认 [LAN_EXPORT_DEFAULT_PORT])
 * - 短式:`110#K4F9M2`(用 [localIpv4] 的 /24 前缀补全;要求两端同网段)
 *
 * [localIpv4] 由调用方提供(见 [localIpv4Address]),这样短式解析是纯函数、可单测。
 */
fun parseLanExportTarget(input: String, localIpv4: String?): LanExportParse {
    val text = input.trim()
    if (text.isEmpty()) return LanExportParse.Invalid
    if (text.startsWith("$SCHEME:", ignoreCase = true)) return parseUriForm(text)
    // 扫到别人的二维码(网址之类)要说"这不是 Kusuri 的码",而不是笼统的格式错误。
    if (text.contains("://") || text.startsWith("//")) return LanExportParse.NotKusuri
    return parsePlainForm(text, localIpv4)
}

/** 口令归一化:统一大写、去掉分隔符,并把易混字符映射回 Crockford 字母表(O→0、I/L→1)。 */
fun normalizeLanExportCode(raw: String): String = buildString {
    raw.trim().uppercase().forEach { character ->
        when (character) {
            'O' -> append('0')
            'I', 'L' -> append('1')
            else -> if (character.isLetterOrDigit()) append(character)
        }
    }
}

/**
 * 本机在局域网里的 IPv4(用于短式补全)。
 *
 * 只挑"已启用、非回环、站点本地"的地址;拿不到就返回 null,界面会要求改用完整式。
 * 不申请任何权限:枚举网卡本身不需要权限(应用已有 INTERNET 权限作为兜底)。
 */
fun localIpv4Address(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .map { it.hostAddress }
        .firstOrNull { address -> isIpv4Literal(address) && isSiteLocal(address) }
}.getOrNull()

private fun parseUriForm(text: String): LanExportParse {
    val body = text.substringAfter(':').removePrefix("//")
    val head = body.substringBefore('?')
    val query = body.substringAfter('?', missingDelimiterValue = "")

    val segments = head.split('/').filter { it.isNotBlank() }
    if (segments.isEmpty() || !segments[0].equals(PURPOSE, ignoreCase = true)) {
        return LanExportParse.NotKusuri
    }
    val version = segments.getOrNull(1)?.toIntOrNull() ?: SUPPORTED_VERSION
    if (version != SUPPORTED_VERSION) return LanExportParse.UnsupportedVersion

    val params = parseQuery(query)
    val host = params["h"] ?: return LanExportParse.Invalid
    val port = params["p"]?.toIntOrNull() ?: LAN_EXPORT_DEFAULT_PORT
    val code = params["c"] ?: return LanExportParse.Invalid
    return buildTarget(host, port, code)
}

private fun parsePlainForm(text: String, localIpv4: String?): LanExportParse {
    val hashIndex = text.lastIndexOf('#')
    if (hashIndex < 0) return LanExportParse.Invalid

    val address = text.substring(0, hashIndex).trim()
    val code = text.substring(hashIndex + 1)
    if (address.isEmpty()) return LanExportParse.Invalid

    val colonIndex = address.lastIndexOf(':')
    val rawHost = if (colonIndex >= 0) address.substring(0, colonIndex).trim() else address
    val port = if (colonIndex >= 0) {
        address.substring(colonIndex + 1).trim().toIntOrNull() ?: return LanExportParse.Invalid
    } else {
        LAN_EXPORT_DEFAULT_PORT
    }
    if (rawHost.isEmpty()) return LanExportParse.Invalid

    val host = if (rawHost.contains('.')) {
        rawHost
    } else {
        // 短式:只给了最后一段,用本机 /24 补全(两端同子网是整个功能的前提)。
        val prefix = localSubnetPrefix(localIpv4) ?: return LanExportParse.NeedsLocalAddress
        "$prefix.$rawHost"
    }
    return buildTarget(host, port, code)
}

private fun parseQuery(query: String): Map<String, String> =
    query.split('&', ';')
        .mapNotNull { pair ->
            val key = pair.substringBefore('=').trim().lowercase()
            if (key.isEmpty() || !pair.contains('=')) {
                null
            } else {
                key to urlDecode(pair.substringAfter('=').trim())
            }
        }
        .toMap()

private fun urlDecode(value: String): String = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrDefault(value)

private fun buildTarget(host: String, port: Int, code: String): LanExportParse {
    if (port !in 1..65535) return LanExportParse.Invalid
    if (!isIpv4Literal(host)) return LanExportParse.Invalid
    val normalized = normalizeLanExportCode(code)
    if (normalized.length != CODE_LENGTH) return LanExportParse.Invalid
    if (normalized.any { it !in CODE_ALPHABET }) return LanExportParse.Invalid
    return LanExportParse.Parsed(LanExportTarget(host = host, port = port, code = normalized))
}

private fun localSubnetPrefix(localIpv4: String?): String? {
    if (localIpv4 == null || !isIpv4Literal(localIpv4)) return null
    return localIpv4.substringBeforeLast('.')
}

/** 只接受 IPv4 字面量,且不接受前导零(避免被当成八进制解析)。 */
private fun isIpv4Literal(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 &&
            part.all { it in '0'..'9' } &&
            (part.length == 1 || part[0] != '0') &&
            part.toInt() in 0..255
    }
}

/** 10/8、172.16/12、192.168/16:家庭与办公局域网的实际范围。 */
private fun isSiteLocal(address: String): Boolean {
    val parts = address.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return false
    return when {
        parts[0] == 10 -> true
        parts[0] == 192 && parts[1] == 168 -> true
        parts[0] == 172 && parts[1] in 16..31 -> true
        else -> false
    }
}
