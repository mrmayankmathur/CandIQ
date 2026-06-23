package com.redrob.ui

import emotion.react.css
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useEffectOnce
import react.useState

val appScope = MainScope()

val App = FC<Props> {
    val (jd, setJd) = useState<JobDescription?>(null)
    val (results, setResults) = useState<List<RankedCandidate>>(emptyList())
    val (loading, setLoading) = useState(true)
    val (searched, setSearched) = useState(false)
    val (selected, setSelected) = useState<String?>(null)
    val (runOpen, setRunOpen) = useState(false)

    useEffectOnce {
        appScope.launch {
            try {
                setJd(Api.fetchJd())
                setResults(Api.fetchResults(100))
            } catch (e: Throwable) {
                console.error("Failed to load initial data", e)
            }
            setLoading(false)
        }
    }

    // ── Page shell ──────────────────────────────────────────────────────────
    div {
        css {
            val s = asDynamic()
            s.maxWidth = "1320px"
            s.margin = "0 auto"
            s.padding = "28px 24px 80px"
        }

        Header {
            this.count = results.size
            this.onRerun = { setRunOpen(true) }
        }

        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.gap = "22px"
                s.alignItems = "flex-start"
                s.marginTop = "22px"
                s.flexWrap = "wrap"
            }

            // Left: job description
            div {
                css {
                    val s = asDynamic()
                    s.flex = "0 0 380px"
                    s.maxWidth = "380px"
                    s.position = "sticky"
                    s.top = "16px"
                }
                jd?.let { JdPanel { this.jd = it } }
            }

            // Right: search + ranked results
            div {
                css {
                    val s = asDynamic()
                    s.flex = "1 1 560px"
                    s.minWidth = "320px"
                }
                ResultsPane {
                    this.jd = jd
                    this.results = results
                    this.loading = loading
                    this.searched = searched
                    this.onSearch = { setSearched(true) }
                    this.onOpen = { id -> setSelected(id) }
                }
            }
        }
    }

    selected?.let { id ->
        ProfileDrawer {
            this.candidateId = id
            this.onClose = { setSelected(null) }
        }
    }

    if (runOpen) {
        RunConsole {
            this.onClose = { setRunOpen(false) }
            this.onComplete = {
                appScope.launch {
                    try {
                        setResults(Api.fetchResults(100))
                    } catch (e: Throwable) {
                        console.error("Reload after run failed", e)
                    }
                }
            }
        }
    }
}

external interface HeaderProps : Props {
    var count: Int
    var onRerun: () -> Unit
}

val Header = FC<HeaderProps> { props ->
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.justifyContent = "space-between"
            s.alignItems = "center"
            s.gap = "16px"
            s.flexWrap = "wrap"
        }
        div {
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
                        s.fontWeight = "800"
                        s.fontSize = "22px"
                        s.background = "linear-gradient(90deg, ${Theme.accent}, ${Theme.accent2})"
                        s.webkitBackgroundClip = "text"
                        s.backgroundClip = "text"
                        s.color = "transparent"
                        s.letterSpacing = "-0.5px"
                    }
                    +"Redrob"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontSize = "13px"
                        s.color = Theme.dim
                        s.padding = "3px 9px"
                        s.border = "1px solid ${Theme.border}"
                        s.borderRadius = "999px"
                    }
                    +"Intelligent Candidate Discovery"
                }
            }
            div {
                css {
                    val s = asDynamic()
                    s.color = Theme.faint
                    s.fontSize = "13px"
                    s.marginTop = "6px"
                }
                +"Hybrid retrieval · honeypot filtering · weighted scoring — top ${props.count} ranked candidates"
            }
        }

        button {
            css {
                val s = asDynamic()
                s.display = "inline-flex"
                s.alignItems = "center"
                s.gap = "8px"
                s.cursor = "pointer"
                s.fontWeight = "600"
                s.fontSize = "14px"
                s.color = Theme.pageText
                s.background = "linear-gradient(90deg, ${Theme.accent}, #5b62e0)"
                s.border = "none"
                s.padding = "11px 18px"
                s.borderRadius = "10px"
                s.boxShadow = "0 6px 20px rgba(124,131,255,0.35)"
            }
            onClick = { props.onRerun() }
            span { +"⟳" }
            +"Re-run Ranking Engine"
        }
    }
}
