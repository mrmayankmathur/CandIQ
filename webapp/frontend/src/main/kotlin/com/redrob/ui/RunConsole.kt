package com.redrob.ui

import emotion.react.css
import kotlinx.coroutines.launch
import org.w3c.dom.EventSource
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import web.html.HTMLDivElement

external interface RunConsoleProps : Props {
    var onClose: () -> Unit
    var onComplete: () -> Unit
}

val RunConsole = FC<RunConsoleProps> { props ->
    val (lines, setLines) = useState(listOf<String>())
    val (status, setStatus) = useState("starting")
    val logRef = useRef<HTMLDivElement>(null)

    useEffectOnce {
        setStatus("running")
        appScope.launch {
            val code = try {
                Api.startRun()
            } catch (e: Throwable) {
                setLines { it + "✗ Could not start run: ${e.message}" }
                setStatus("error")
                return@launch
            }
            if (code == 409) {
                setLines { it + "A run is already in progress — attaching to its log stream…" }
            }
            val es = EventSource("/api/rank/stream")
            es.addEventListener("log", { e ->
                val data = e.asDynamic().data as? String ?: ""
                setLines { it + data }
            })
            es.addEventListener("done", { _ ->
                setStatus("done")
                es.close()
                props.onComplete()
            })
            es.addEventListener("error", { e ->
                val data = e.asDynamic().data as? String ?: "stream error"
                setLines { it + "✗ $data" }
                setStatus("error")
                es.close()
            })
        }
    }

    // Auto-scroll the log to the bottom as lines arrive.
    useEffect(lines.size) {
        val el = logRef.current
        if (el != null) {
            el.scrollTop = el.scrollHeight.toDouble()
        }
    }

    val running = status == "running" || status == "starting"

    // Backdrop
    div {
        css {
            val s = asDynamic()
            s.position = "fixed"
            s.top = "0"; s.left = "0"; s.right = "0"; s.bottom = "0"
            s.background = "rgba(5,6,15,0.70)"
            s.display = "flex"
            s.alignItems = "center"
            s.justifyContent = "center"
            s.zIndex = "60"
            s.padding = "24px"
        }
        onClick = { if (!running) props.onClose() }

        div {
            css {
                val s = asDynamic()
                s.width = "min(820px, 96vw)"
                s.background = "#0c0f1f"
                s.border = "1px solid ${Theme.border}"
                s.borderRadius = "16px"
                s.overflow = "hidden"
                s.boxShadow = "0 30px 80px rgba(0,0,0,0.6)"
            }
            onClick = { it.stopPropagation() }

            // Title bar
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.alignItems = "center"
                    s.justifyContent = "space-between"
                    s.padding = "14px 18px"
                    s.borderBottom = "1px solid ${Theme.border}"
                }
                div {
                    css {
                        val s = asDynamic()
                        s.display = "flex"
                        s.alignItems = "center"
                        s.gap = "10px"
                    }
                    span {
                        css {
                            val s = asDynamic()
                            s.width = "11px"
                            s.height = "11px"
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
                            s.fontWeight = "700"
                            s.fontSize = "15px"
                        }
                        +"Live Ranking Engine"
                    }
                    span {
                        css {
                            val s = asDynamic()
                            s.fontSize = "12px"
                            s.color = Theme.dim
                            s.fontFamily = Theme.mono
                        }
                        +when (status) {
                            "done" -> "completed · results reloaded"
                            "error" -> "failed"
                            else -> "running python -m ranker.rank …"
                        }
                    }
                }
                button {
                    css {
                        val s = asDynamic()
                        s.cursor = if (running) "not-allowed" else "pointer"
                        s.opacity = if (running) "0.4" else "1"
                        s.background = "transparent"
                        s.border = "1px solid ${Theme.border}"
                        s.color = Theme.pageText
                        s.borderRadius = "8px"
                        s.padding = "6px 12px"
                        s.fontSize = "13px"
                    }
                    disabled = running
                    onClick = { if (!running) props.onClose() }
                    +"Close"
                }
            }

            // Log output
            div {
                ref = logRef
                css {
                    val s = asDynamic()
                    s.fontFamily = Theme.mono
                    s.fontSize = "12.5px"
                    s.lineHeight = "1.6"
                    s.color = "#c8cdf0"
                    s.background = "#070912"
                    s.padding = "16px 18px"
                    s.height = "52vh"
                    s.overflowY = "auto"
                    s.whiteSpace = "pre-wrap"
                    s.wordBreak = "break-word"
                }
                if (lines.isEmpty()) {
                    div {
                        css {
                            val s = asDynamic()
                            s.color = Theme.faint
                        }
                        +"Starting the ranking engine… (full run is ~60–90s)"
                    }
                }
                lines.forEach { line ->
                    div { +line }
                }
            }
        }
    }
}
