package com.redrob.ui

import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

external interface JdPanelProps : Props {
    var jd: JobDescription
}

val JdPanel = FC<JdPanelProps> { props ->
    val jd = props.jd
    div {
        css {
            val s = asDynamic()
            s.background = Theme.card
            s.border = "1px solid ${Theme.border}"
            s.borderRadius = "16px"
            s.padding = "22px"
            s.boxShadow = "0 12px 40px rgba(0,0,0,0.35)"
        }

        div {
            css {
                val s = asDynamic()
                s.fontSize = "11px"
                s.fontWeight = "700"
                s.letterSpacing = "1.2px"
                s.textTransform = "uppercase"
                s.color = Theme.accent
            }
            +"Open Role"
        }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "19px"
                s.fontWeight = "700"
                s.lineHeight = "1.3"
                s.margin = "8px 0 4px"
            }
            +jd.jobTitle
        }
        div {
            css {
                val s = asDynamic()
                s.color = Theme.dim
                s.fontSize = "13px"
            }
            +"${jd.company} · ${jd.location.workMode ?: ""}"
        }
        div {
            css {
                val s = asDynamic()
                s.color = Theme.dim
                s.fontSize = "13.5px"
                s.lineHeight = "1.6"
                s.marginTop = "12px"
            }
            +jd.summary
        }

        // Experience + location facts
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.gap = "10px"
                s.flexWrap = "wrap"
                s.marginTop = "14px"
            }
            Chip {
                text = "Ideal ${jd.experience.idealMin}-${jd.experience.idealMax} yrs"
                strong = true
            }
            Chip { text = "Stated ${jd.experience.statedMin}-${jd.experience.statedMax} yrs"; strong = false }
            Chip { text = "Notice ≤ ${jd.location.noticePeriodIdealDays}d"; strong = false }
        }

        SectionLabel { text = "Preferred Locations" }
        div {
            css { chipRow() }
            jd.location.preferred.forEach { Chip { text = "★ $it"; strong = true } }
            jd.location.acceptable.forEach { Chip { text = it; strong = false } }
        }

        SectionLabel { text = "Must-have Skills" }
        jd.mustHave.forEach { group -> SkillGroupBlock { this.group = group; this.must = true } }

        SectionLabel { text = "Nice-to-have" }
        div {
            css { chipRow() }
            jd.niceToHave.forEach { Chip { text = it.category; strong = false } }
        }

        SectionLabel { text = "Disqualifiers" }
        jd.disqualifiers.forEach { d ->
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.gap = "8px"
                    s.alignItems = "flex-start"
                    s.fontSize = "12.5px"
                    s.color = Theme.dim
                    s.marginBottom = "7px"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.color = if (d.severity == "hard_reject") Theme.bad else Theme.warn
                        s.fontWeight = "700"
                    }
                    +(if (d.severity == "hard_reject") "✕" else "!")
                }
                span { +d.description }
            }
        }
    }
}

private external interface SkillGroupProps : Props {
    var group: SkillGroup
    var must: Boolean
}

private val SkillGroupBlock = FC<SkillGroupProps> { props ->
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
                s.marginBottom = "6px"
            }
            span {
                css {
                    val s = asDynamic()
                    s.fontSize = "13.5px"
                    s.fontWeight = "600"
                    s.color = Theme.pageText
                }
                +props.group.category
            }
            span {
                css {
                    val s = asDynamic()
                    s.fontSize = "11px"
                    s.color = Theme.faint
                    s.fontFamily = Theme.mono
                }
                +"w=${props.group.weight}"
            }
        }
        div {
            css { chipRow() }
            props.group.keywords.take(8).forEach { Chip { text = it; strong = false } }
        }
    }
}

// emotion css receiver helper: a flex-wrap row of chips
private fun Any?.chipRow() {
    val s = this.asDynamic()
    s.display = "flex"
    s.gap = "7px"
    s.flexWrap = "wrap"
}
