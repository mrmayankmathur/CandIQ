package com.redrob.ui

import emotion.react.css
import org.w3c.dom.EventSource
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import web.html.HTMLDivElement

external interface AiDeepDiveProps : Props {
    var candidateId: String
}

val AiDeepDive = FC<AiDeepDiveProps> { props ->
    val (text, setText) = useState("")
    val (status, setStatus) = useState("connecting")
    val logRef = useRef<HTMLDivElement>(null)

    useEffectOnce {
        val es = EventSource("/api/candidates/${props.candidateId}/deepdive")
        
        es.addEventListener("token", { e ->
            val data = e.asDynamic().data as? String ?: ""
            setText { it + data }
        })
        
        es.addEventListener("done", { _ ->
            setStatus("done")
            es.close()
        })
        
        es.addEventListener("info", { e ->
            val data = e.asDynamic().data as? String ?: ""
            setText { it + "\n" + data }
            setStatus("done")
            es.close()
        })
        
        es.addEventListener("error", { e ->
            val data = e.asDynamic().data as? String ?: "stream error"
            setText { it + "\n✗ " + data }
            setStatus("error")
            es.close()
        })


    }

    // Auto-scroll to the bottom as text arrives
    useEffect(text.length) {
        val el = logRef.current
        if (el != null) {
            el.scrollTop = el.scrollHeight.toDouble()
        }
    }

    div {
        css {
            val s = asDynamic()
            s.marginTop = "14px"
            s.background = "linear-gradient(180deg, rgba(124,131,255,0.10), rgba(34,211,238,0.05))"
            s.border = "1px solid rgba(124,131,255,0.30)"
            s.borderRadius = "12px"
            s.padding = "14px 16px"
            s.fontSize = "13.5px"
            s.lineHeight = "1.65"
            s.color = "#dfe2f5"
        }
        
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.alignItems = "center"
                s.gap = "8px"
                s.marginBottom = "10px"
                s.borderBottom = "1px solid rgba(124,131,255,0.20)"
                s.paddingBottom = "8px"
            }
            span {
                css {
                    val s = asDynamic()
                    s.width = "9px"
                    s.height = "9px"
                    s.borderRadius = "50%"
                    s.display = "inline-block"
                    s.background = when (status) {
                        "done" -> Theme.good
                        "error" -> Theme.bad
                        else -> Theme.warn
                    }
                }
            }
            span {
                css {
                    val s = asDynamic()
                    s.fontWeight = "600"
                    s.color = Theme.accent
                }
                +"AI Deep-Dive"
            }
        }
        
        div {
            ref = logRef
            css {
                val s = asDynamic()
                s.maxHeight = "400px"
                s.overflowY = "auto"
                s.whiteSpace = "pre-wrap"
                s.wordBreak = "break-word"
            }
            +text
            if (status == "connecting") {
                span {
                    css {
                        val s = asDynamic()
                        s.color = Theme.faint
                        s.fontStyle = "italic"
                    }
                    +" Connecting to AI..."
                }
            }
        }
    }
}
