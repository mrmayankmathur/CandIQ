package com.redrob.ui

import emotion.react.css
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState

external interface ProfileDrawerProps : Props {
    var candidateId: String
    var onClose: () -> Unit
}

val ProfileDrawer = FC<ProfileDrawerProps> { props ->
    val (detail, setDetail) = useState<CandidateDetail?>(null)
    val (loading, setLoading) = useState(true)

    useEffect(props.candidateId) {
        setLoading(true)
        setDetail(null)
        appScope.launch {
            try {
                setDetail(Api.fetchCandidate(props.candidateId))
            } catch (e: Throwable) {
                console.error("Failed to load candidate ${props.candidateId}", e)
            }
            setLoading(false)
        }
    }

    // Backdrop
    div {
        css {
            val s = asDynamic()
            s.position = "fixed"
            s.top = "0"; s.left = "0"; s.right = "0"; s.bottom = "0"
            s.background = "rgba(5,6,15,0.62)"
            s.backdropFilter = "blur(2px)"
            s.display = "flex"
            s.justifyContent = "flex-end"
            s.zIndex = "50"
        }
        onClick = { props.onClose() }

        // Panel
        div {
            css {
                val s = asDynamic()
                s.width = "min(580px, 94vw)"
                s.height = "100vh"
                s.overflowY = "auto"
                s.background = "#0f1226"
                s.borderLeft = "1px solid ${Theme.border}"
                s.padding = "24px 26px 60px"
                s.boxShadow = "-20px 0 60px rgba(0,0,0,0.5)"
            }
            onClick = { it.stopPropagation() }

            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.justifyContent = "flex-end"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.cursor = "pointer"
                        s.color = Theme.dim
                        s.fontSize = "22px"
                        s.lineHeight = "1"
                    }
                    onClick = { props.onClose() }
                    +"✕"
                }
            }

            when {
                loading -> Centered { text = "Loading profile…" }
                detail == null -> Centered { text = "Profile unavailable." }
                else -> ProfileBody { this.detail = detail!! }
            }
        }
    }
}

private external interface CenteredProps : Props {
    var text: String
}

private val Centered = FC<CenteredProps> { props ->
    div {
        css {
            val s = asDynamic()
            s.padding = "60px 0"
            s.textAlign = "center"
            s.color = Theme.dim
        }
        +props.text
    }
}

private external interface ProfileBodyProps : Props {
    var detail: CandidateDetail
}

private val ProfileBody = FC<ProfileBodyProps> { props ->
    val d = props.detail
    val p = d.profile.profile
    val r = d.ranking

    // Header
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.alignItems = "center"
            s.gap = "12px"
            s.marginBottom = "4px"
        }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "22px"
                s.fontWeight = "800"
            }
            +(p.anonymizedName ?: d.profile.candidateId)
        }
        Chip { text = "Rank #${r.rank}"; strong = true }
        Chip { text = "score ${r.score.asScore()}"; strong = false }
    }
    div {
        css {
            val s = asDynamic()
            s.color = Theme.dim
            s.fontSize = "14px"
        }
        +(p.headline ?: "")
    }
    div {
        css {
            val s = asDynamic()
            s.color = Theme.faint
            s.fontSize = "13px"
            s.marginTop = "6px"
        }
        +listOfNotNull(
            p.currentTitle,
            p.currentCompany?.let { "@ $it" },
            "${p.yearsOfExperience} yrs",
            p.location,
            p.country,
        ).joinToString(" · ")
    }

    // AI reasoning
    SectionLabel { text = "AI Reasoning" }
    div {
        css {
            val s = asDynamic()
            s.background = "linear-gradient(180deg, rgba(124,131,255,0.10), rgba(34,211,238,0.05))"
            s.border = "1px solid rgba(124,131,255,0.30)"
            s.borderRadius = "12px"
            s.padding = "14px 16px"
            s.fontSize = "13.5px"
            s.lineHeight = "1.65"
            s.color = "#dfe2f5"
        }
        +(r.reasoning ?: "No reasoning available.")
    }

    // Match breakdown
    SectionLabel { text = "Match Breakdown" }
    d.match.factors.forEach { f ->
        FitBar {
            label = f.label
            value = f.value
            detail = f.detail
        }
    }
    if (d.match.matchedSkills.isNotEmpty()) {
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.gap = "7px"
                s.flexWrap = "wrap"
                s.marginTop = "6px"
            }
            d.match.matchedSkills.forEach { Chip { text = it; strong = true } }
        }
    }
    d.match.note?.let { note ->
        div {
            css {
                val s = asDynamic()
                s.fontSize = "11.5px"
                s.color = Theme.faint
                s.marginTop = "10px"
                s.fontStyle = "italic"
            }
            +note
        }
    }

    // Summary
    p.summary?.takeIf { it.isNotBlank() }?.let { summary ->
        SectionLabel { text = "Summary" }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "13.5px"
                s.lineHeight = "1.65"
                s.color = Theme.dim
            }
            +summary
        }
    }

    // Career
    SectionLabel { text = "Career History" }
    d.profile.careerHistory.forEach { job ->
        div {
            css {
                val s = asDynamic()
                s.borderLeft = "2px solid ${Theme.border}"
                s.paddingLeft = "14px"
                s.marginBottom = "14px"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "14px"
                    s.fontWeight = "600"
                }
                +"${job.title ?: ""} · ${job.company ?: ""}"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "12px"
                    s.color = Theme.faint
                    s.margin = "2px 0 6px"
                }
                +"${job.startDate ?: ""} – ${job.endDate ?: "present"} · ${job.durationMonths} mo · ${job.industry ?: ""}"
            }
            job.description?.let {
                div {
                    css {
                        val s = asDynamic()
                        s.fontSize = "12.5px"
                        s.color = Theme.dim
                        s.lineHeight = "1.55"
                    }
                    +it
                }
            }
        }
    }

    // Skills
    if (d.profile.skills.isNotEmpty()) {
        SectionLabel { text = "Skills" }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.gap = "7px"
                s.flexWrap = "wrap"
            }
            d.profile.skills.sortedByDescending { it.endorsements }.take(20).forEach { sk ->
                Chip { text = "${sk.name} · ${sk.proficiency ?: ""}"; strong = false }
            }
        }
    }

    // Education
    if (d.profile.education.isNotEmpty()) {
        SectionLabel { text = "Education" }
        d.profile.education.forEach { e ->
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "13px"
                    s.color = Theme.dim
                    s.marginBottom = "6px"
                }
                +"${e.degree ?: ""} ${e.fieldOfStudy ?: ""} — ${e.institution ?: ""} (${e.endYear})"
            }
        }
    }

    // Signals
    SectionLabel { text = "Redrob Signals" }
    div {
        css {
            val s = asDynamic()
            s.display = "grid"
            s.gridTemplateColumns = "repeat(2, 1fr)"
            s.gap = "10px"
        }
        val sg = d.profile.signals
        SignalCell { label = "Open to work"; value = if (sg.openToWork) "Yes" else "No" }
        SignalCell { label = "Notice period"; value = "${sg.noticePeriodDays} days" }
        SignalCell { label = "Response rate"; value = sg.recruiterResponseRate.asScore() }
        SignalCell { label = "Last active"; value = sg.lastActiveDate ?: "—" }
        SignalCell { label = "GitHub activity"; value = sg.githubActivityScore.toInt().toString() }
        SignalCell { label = "Profile complete"; value = "${sg.profileCompletenessScore.toInt()}%" }
        SignalCell { label = "Work mode"; value = sg.preferredWorkMode ?: "—" }
        SignalCell { label = "Expected (LPA)"; value = "${sg.expectedSalaryLpa.min.toInt()}–${sg.expectedSalaryLpa.max.toInt()}" }
    }
}

private external interface SignalCellProps : Props {
    var label: String
    var value: String
}

private val SignalCell = FC<SignalCellProps> { props ->
    div {
        css {
            val s = asDynamic()
            s.background = Theme.card
            s.border = "1px solid ${Theme.borderSoft}"
            s.borderRadius = "10px"
            s.padding = "10px 12px"
        }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "11px"
                s.color = Theme.faint
                s.textTransform = "uppercase"
                s.letterSpacing = "0.6px"
            }
            +props.label
        }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "14px"
                s.fontWeight = "600"
                s.marginTop = "3px"
            }
            +props.value
        }
    }
}
