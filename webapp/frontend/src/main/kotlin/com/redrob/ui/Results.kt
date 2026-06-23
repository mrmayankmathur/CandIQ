package com.redrob.ui

import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

external interface ResultsPaneProps : Props {
    var jd: JobDescription?
    var results: List<RankedCandidate>
    var loading: Boolean
    var searched: Boolean
    var onSearch: () -> Unit
    var onOpen: (String) -> Unit
}

val ResultsPane = FC<ResultsPaneProps> { props ->
    // Search bar
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.gap = "10px"
            s.alignItems = "center"
            s.background = Theme.card
            s.border = "1px solid ${Theme.border}"
            s.borderRadius = "14px"
            s.padding = "10px 12px"
            s.boxShadow = "0 8px 30px rgba(0,0,0,0.30)"
        }
        span {
            css {
                val s = asDynamic()
                s.fontSize = "17px"
                s.paddingLeft = "4px"
            }
            +"🔍"
        }
        div {
            css {
                val s = asDynamic()
                s.flex = "1"
                s.color = Theme.pageText
                s.fontSize = "15px"
                s.fontWeight = "500"
                s.whiteSpace = "nowrap"
                s.overflow = "hidden"
                s.textOverflow = "ellipsis"
            }
            +(props.jd?.jobTitle ?: "Senior AI/ML Engineer")
        }
        button {
            css {
                val s = asDynamic()
                s.cursor = "pointer"
                s.fontWeight = "600"
                s.fontSize = "14px"
                s.color = "#0b0d1a"
                s.background = "linear-gradient(90deg, ${Theme.accent2}, #38bdf8)"
                s.border = "none"
                s.padding = "10px 18px"
                s.borderRadius = "10px"
            }
            onClick = { props.onSearch() }
            +"Search Candidates"
        }
    }

    when {
        props.loading -> Notice { text = "Loading ranked candidates…" }
        !props.searched -> Notice {
            text = "Press “Search Candidates” to reveal the engine's ranked shortlist for this role."
        }
        props.results.isEmpty() -> Notice { text = "No results available." }
        else -> {
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.justifyContent = "space-between"
                    s.alignItems = "baseline"
                    s.margin = "20px 4px 12px"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontSize = "14px"
                        s.color = Theme.dim
                    }
                    +"${props.results.size} candidates ranked by composite fit"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontSize = "12px"
                        s.color = Theme.faint
                    }
                    +"click a card for full profile + AI reasoning"
                }
            }
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.flexDirection = "column"
                    s.gap = "12px"
                }
                props.results.forEach { item ->
                    CandidateCard {
                        key = item.ranking.candidateId.unsafeCast<react.Key>()
                        this.item = item
                        this.onOpen = props.onOpen
                    }
                }
            }
        }
    }
}

private external interface NoticeProps : Props {
    var text: String
}

private val Notice = FC<NoticeProps> { props ->
    div {
        css {
            val s = asDynamic()
            s.marginTop = "20px"
            s.padding = "40px 24px"
            s.textAlign = "center"
            s.color = Theme.dim
            s.fontSize = "14px"
            s.background = Theme.card
            s.border = "1px dashed ${Theme.border}"
            s.borderRadius = "14px"
        }
        +props.text
    }
}

external interface CandidateCardProps : Props {
    var item: RankedCandidate
    var onOpen: (String) -> Unit
}

val CandidateCard = FC<CandidateCardProps> { props ->
    val r = props.item.ranking
    val p = props.item.summary
    val pct = (r.score.coerceIn(0.0, 1.0) * 100).toInt()
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.gap = "16px"
            s.alignItems = "stretch"
            s.background = Theme.card
            s.border = "1px solid ${Theme.border}"
            s.borderRadius = "14px"
            s.padding = "16px 18px"
            s.cursor = "pointer"
            s.transition = "border-color 0.15s ease, transform 0.15s ease"
        }
        onClick = { props.onOpen(r.candidateId) }

        // Rank badge
        div {
            css {
                val s = asDynamic()
                s.flex = "0 0 44px"
                s.display = "flex"
                s.flexDirection = "column"
                s.alignItems = "center"
                s.justifyContent = "center"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "20px"
                    s.fontWeight = "800"
                    s.color = if (r.rank <= 3) Theme.accent2 else Theme.pageText
                }
                +"#${r.rank}"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "11px"
                    s.color = Theme.faint
                    s.fontFamily = Theme.mono
                }
                +r.score.asScore()
            }
        }

        // Main
        div {
            css {
                val s = asDynamic()
                s.flex = "1"
                s.minWidth = "0"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "15.5px"
                    s.fontWeight = "700"
                }
                +(p?.name ?: r.candidateId)
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "13px"
                    s.color = Theme.dim
                    s.marginTop = "2px"
                    s.whiteSpace = "nowrap"
                    s.overflow = "hidden"
                    s.textOverflow = "ellipsis"
                }
                +(p?.headline ?: "")
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "12.5px"
                    s.color = Theme.faint
                    s.marginTop = "4px"
                }
                +listOfNotNull(
                    p?.currentTitle,
                    p?.currentCompany?.let { "@ $it" },
                    p?.yearsOfExperience?.let { "${it} yrs" },
                    p?.location,
                ).joinToString(" · ")
            }
            // chips row
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.gap = "7px"
                    s.flexWrap = "wrap"
                    s.marginTop = "10px"
                }
                if (p?.openToWork == true) Chip { text = "Open to work"; strong = true }
                p?.topSkills?.take(4)?.forEach { Chip { text = it; strong = false } }
            }
        }

        // Score bar (vertical hint at right)
        div {
            css {
                val s = asDynamic()
                s.flex = "0 0 90px"
                s.display = "flex"
                s.flexDirection = "column"
                s.justifyContent = "center"
                s.gap = "6px"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "11px"
                    s.color = Theme.faint
                    s.textAlign = "right"
                }
                +"fit $pct%"
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
                        s.background = "linear-gradient(90deg, ${Theme.accent}, ${Theme.accent2})"
                    }
                }
            }
        }
    }
}

/** Format a 0..1 score like "0.875". */
fun Double.asScore(): String {
    val scaled = (this * 1000).toInt()
    val whole = scaled / 1000
    val frac = (scaled % 1000).toString().padStart(3, '0')
    return "$whole.$frac"
}
