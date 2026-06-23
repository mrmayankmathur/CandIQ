package com.redrob.ui

import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

/** A small rounded tag. */
external interface ChipProps : Props {
    var text: String
    var strong: Boolean
}

val Chip = FC<ChipProps> { props ->
    span {
        css {
            val s = asDynamic()
            s.display = "inline-block"
            s.fontSize = "12px"
            s.lineHeight = "1.4"
            s.padding = "3px 9px"
            s.borderRadius = "999px"
            s.whiteSpace = "nowrap"
            if (props.strong) {
                s.color = Theme.accent2
                s.background = "rgba(34,211,238,0.10)"
                s.border = "1px solid rgba(34,211,238,0.35)"
            } else {
                s.color = Theme.dim
                s.background = Theme.raised
                s.border = "1px solid ${Theme.border}"
            }
        }
        +props.text
    }
}

/** A labelled 0..1 progress bar, colored along a fit scale. */
external interface BarProps : Props {
    var label: String
    var value: Double
    var detail: String?
}

val FitBar = FC<BarProps> { props ->
    val pct = (props.value.coerceIn(0.0, 1.0) * 100).toInt()
    div {
        css {
            val s = asDynamic()
            s.marginBottom = "12px"
        }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.justifyContent = "space-between"
                s.alignItems = "baseline"
                s.marginBottom = "5px"
            }
            span {
                css {
                    val s = asDynamic()
                    s.fontSize = "13px"
                    s.fontWeight = "600"
                    s.color = Theme.pageText
                }
                +props.label
            }
            span {
                css {
                    val s = asDynamic()
                    s.fontSize = "12px"
                    s.color = Theme.dim
                    s.fontFamily = Theme.mono
                }
                +"$pct%"
            }
        }
        div {
            css {
                val s = asDynamic()
                s.height = "8px"
                s.width = "100%"
                s.background = Theme.raised
                s.borderRadius = "999px"
                s.overflow = "hidden"
            }
            div {
                css {
                    val s = asDynamic()
                    s.height = "100%"
                    s.width = "$pct%"
                    s.borderRadius = "999px"
                    s.background = Theme.fitColor(props.value)
                    s.transition = "width 0.4s ease"
                }
            }
        }
        props.detail?.let { d ->
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "12px"
                    s.color = Theme.faint
                    s.marginTop = "4px"
                }
                +d
            }
        }
    }
}

/** Section heading inside a card. */
external interface LabelProps : Props {
    var text: String
}

val SectionLabel = FC<LabelProps> { props ->
    div {
        css {
            val s = asDynamic()
            s.fontSize = "11px"
            s.fontWeight = "700"
            s.letterSpacing = "1.2px"
            s.textTransform = "uppercase"
            s.color = Theme.faint
            s.marginBottom = "10px"
            s.marginTop = "18px"
        }
        +props.text
    }
}
