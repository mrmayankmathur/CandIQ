package com.redrob.ui

import emotion.react.css
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
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
    val (theme, setTheme) = useState("light")

    useEffectOnce {
        appScope.launch {
            try {
                setJd(Api.fetchJd())
                val fetchedResults = Api.fetchResults(100)
                setResults(fetchedResults)
                if (fetchedResults.isNotEmpty()) {
                    setSearched(true)
                }
            } catch (e: Throwable) {
                console.error("Failed to load initial data", e)
            }
            setLoading(false)
        }
    }

    val toggleTheme: () -> Unit = {
        val newTheme = if (theme == "light") "dark" else "light"
        setTheme(newTheme)
        kotlinx.browser.document.body?.setAttribute("data-theme", newTheme)
    }

    // ── Page shell ──────────────────────────────────────────────────────────
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.flex = "1"
            s.width = "100%"
            s.minHeight = "100vh"
            s.background = Theme.bg
        }



        div {
            css {
                val s = asDynamic()
                s.flex = "1"
                s.padding = "32px 40px"
                s.display = "flex"
                s.flexDirection = "column"
                s.width = "100%"
            }

            Header {
                this.theme = theme
                this.toggleTheme = toggleTheme
            }

            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.gap = "24px"
                    s.alignItems = "flex-start"
                    s.marginTop = "24px"
                }

                // Left: job description
                div {
                    css {
                        val s = asDynamic()
                        s.flex = "0 0 320px"
                        s.position = "sticky"
                        s.top = "24px"
                    }
                    jd?.let { 
                        JdPanel { 
                            this.jd = it
                            this.onRerun = {
                                setSearched(true)
                                setRunOpen(true)
                            }
                        } 
                    }
                }

                // Right: search + ranked results
                div {
                    css {
                        val s = asDynamic()
                        s.flex = "1 1 auto"
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
    var theme: String
    var toggleTheme: () -> Unit
}

val Header = FC<HeaderProps> { props ->
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.justifyContent = "space-between"
            s.alignItems = "center"
            s.marginBottom = "8px"
            s.paddingBottom = "16px"
            s.borderBottom = "1px solid ${Theme.borderSoft}"
            
            // Sticky Header
            s.position = "sticky"
            s.top = "0"
            s.zIndex = "10"
            s.background = Theme.bg
            s.margin = "-32px -40px 8px -40px"
            s.padding = "32px 40px 16px 40px"
        }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.alignItems = "center"
                s.gap = "10px"
            }
            // Logo
            img {
                src = "logo.png"
                alt = "CandIQ Logo"
                css {
                    val s = asDynamic()
                    s.width = "200px"
                    s.height = "40px"
                    s.borderRadius = "11px"
                    s.objectFit = "contain"
                }
            }
            // Divider
            div {
                css {
                    val s = asDynamic()
                    s.width = "1px"
                    s.height = "45px"
                    s.backgroundColor =
                        if (props.theme == "light") "#E2E5E9"
                        else "#212121"
                    s.margin = "0 6px 0px 8px"
                    s.flexShrink = 0
                }
            }
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.flexDirection = "column"
                    s.gap = "3px"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontWeight = "500"
                        s.fontSize = "20px"
                        s.color = Theme.pageText
                        s.fontFamily = Theme.headingFont
                        s.letterSpacing = "-0.5px"
                        s.lineHeight = "1.1"
                    }
                    +"Intelligent Candidate Discovery"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontSize = "12.5px"
                        s.color = Theme.dim
                    }
                    +"AI-ranked Shortlist"
                }
            }
        }
        div {
            css {
                val s = asDynamic()
                s.cursor = "pointer"
                s.fontSize = "20px"
                s.display = "flex"
                s.alignItems = "center"
                s.justifyContent = "center"
                s.width = "40px"
                s.height = "40px"
                s.borderRadius = "50%"
                s.background = Theme.card
                s.border = "1px solid ${Theme.borderSoft}"
                s.transition = "all 0.2s"
                val hoverObj = js("{}")
                hoverObj.background = Theme.borderSoft
                s["&:hover"] = hoverObj
                s.userSelect = "none"
                s.webkitUserSelect = "none"
            }
            onClick = { props.toggleTheme() }
            (if (props.theme == "light") {
                img {
                src = "darkToggle.png"
                alt = "Dark Mode Toggle"
                css {
                    val s = asDynamic()
                    s.width = "20px"
                    s.height = "20px"
                    s.borderRadius = "11px"
                    s.objectFit = "contain"
                }
            }
            } else {
                img {
                    src = "lightToggle.png"
                    alt = "Light Mode Toggle"
                    css {
                        val s = asDynamic()
                        s.width = "20px"
                        s.height = "20px"
                        s.borderRadius = "11px"
                        s.objectFit = "contain"
                    }
                }
            })
        }
    }
}
