package moe.lizi.kusuri.data.lan

/**
 * 局域网导出的领域模型:目标、推送类型与结果。
 *
 * 枚举里的字符串值与电脑端脚本逐一对应,改这里就等于改契约(docs/lan-export.md §3.1)。
 */

/** 一次推送发什么。 */
enum class LanExportKind(val headerValue: String, val contentType: String, val extension: String) {
    /** 全量备份(JSON),与 SAF 导出同一份字节。 */
    JSON("json", "application/json; charset=utf-8", "json"),

    /** 服药记录(CSV,给医生看),与 SAF 导出同一份字节。 */
    RECORDS("records", "text/csv; charset=utf-8", "csv"),
}

/**
 * 一次推送的目标:二维码里打包的 host + port + 会话口令。
 *
 * 口令只在内存里活一次,不落盘、不进 SharedPreferences(脚本每次重启都会换)。
 */
data class LanExportTarget(val host: String, val port: Int, val code: String) {
    val display: String get() = "$host:$port"
}

/** 解析结果。失败原因要分得开,界面才能给出不同的下一步。 */
sealed interface LanExportParse {
    data class Parsed(val target: LanExportTarget) : LanExportParse

    /** 扫到/输入的东西根本不是 Kusuri 的码(比如某个网址)。 */
    data object NotKusuri : LanExportParse

    /** 认得是 Kusuri 的码,但版本比本应用新。 */
    data object UnsupportedVersion : LanExportParse

    /** 短式写法需要本机网段来补全,但拿不到本机 IPv4。 */
    data object NeedsLocalAddress : LanExportParse

    /** 看着像我们的格式,但字段不合法(IP / 端口 / 口令)。 */
    data object Invalid : LanExportParse
}

/** 连不上的细分原因:界面按这个给不同的排查提示(docs/lan-export.md §5)。 */
enum class Reachability {
    /** 电脑上没在监听:脚本没跑,或者防火墙挡了。 */
    REFUSED,

    /** 和电脑不在同一个网络(或不同网段、AP 隔离)。 */
    NO_ROUTE,

    /** 地址本身不合法。 */
    UNKNOWN_HOST,

    /** 连上了,但中途断了(发送被打断、Wi-Fi 掉线)。 */
    DROPPED,

    OTHER,
}

/** 发送的哪个阶段超时了。 */
enum class SendStage { CONNECT, READ }

/** 推送失败的原因。文案在界面层(docs/plan.md:文案全资源化)。 */
sealed interface LanExportFailure {
    data class Unreachable(val kind: Reachability, val detail: String) : LanExportFailure

    data class Timeout(val stage: SendStage) : LanExportFailure

    /** 口令不匹配(401):脚本重启会换口令,重新扫码即可。 */
    data object BadCode : LanExportFailure

    /** 电脑拒收:超过 32MB(413)。 */
    data object TooLarge : LanExportFailure

    /** 电脑明确拒绝(400 / 404 / 405 / 422 / 5xx),带原始状态码便于排查。 */
    data class Rejected(val status: Int, val error: String?) : LanExportFailure

    /** 应答看不懂(不是 HTTP、没有 JSON 体、或长得离谱)。 */
    data object BadResponse : LanExportFailure

    /** 电脑说成功了,但校验值对不上——数据在链路上被改过,必须当成失败看待。 */
    data class IntegrityMismatch(val expected: String, val actual: String) : LanExportFailure
}

/** 推送结果。 */
sealed interface LanExportResult {
    /** [sha256] 与发送字节一致,已被电脑落盘到 [savedPath]。 */
    data class Delivered(val savedPath: String, val bytes: Long, val sha256: String) : LanExportResult

    data class Failed(val failure: LanExportFailure) : LanExportResult
}
