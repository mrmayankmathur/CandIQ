package com.redrob.ui

/** Color + spacing tokens. Styling is applied via emotion css { asDynamic()... } string properties. */
object Theme {
    const val pageText = "#e7e9f3"
    const val dim = "#9aa0c0"
    const val faint = "#6b7196"

    const val card = "#13162a"
    const val card2 = "#171b33"
    const val raised = "#1d2240"
    const val border = "#262a47"
    const val borderSoft = "#20243f"

    const val accent = "#7c83ff"
    const val accent2 = "#22d3ee"
    const val good = "#34d399"
    const val warn = "#fbbf24"
    const val bad = "#f87171"

    const val mono = "'JetBrains Mono', ui-monospace, monospace"

    /** Color along a green→amber→red scale for a 0..1 fit value. */
    fun fitColor(v: Double): String = when {
        v >= 0.75 -> good
        v >= 0.5 -> "#a3e635"
        v >= 0.3 -> warn
        else -> bad
    }
}
