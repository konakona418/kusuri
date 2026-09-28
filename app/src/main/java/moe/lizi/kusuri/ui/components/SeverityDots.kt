package moe.lizi.kusuri.ui.components

import moe.lizi.kusuri.domain.log.MAX_SEVERITY

/** 严重程度用点表示:●●●○○(1–5)。 */
fun severityDots(severity: Int): String {
    val filled = severity.coerceIn(0, MAX_SEVERITY)
    val empty = (MAX_SEVERITY - filled).coerceAtLeast(0)
    return "●".repeat(filled) + "○".repeat(empty)
}
