package moe.lizi.kusuri.data.backup

/** 极简 CSV 生成:按 RFC 4180 转义,CRLF 换行(Excel 友好)。 */
fun toCsv(header: List<String>, rows: List<List<String>>): String = buildString {
    append(header.joinToString(",", transform = ::escapeCsv))
    append("\r\n")
    rows.forEach { row ->
        append(row.joinToString(",", transform = ::escapeCsv))
        append("\r\n")
    }
}

internal fun escapeCsv(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
