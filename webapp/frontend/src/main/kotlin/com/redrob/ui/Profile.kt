package com.redrob.ui

import emotion.react.css
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.button
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
            s.background = "rgba(15, 15, 15, 0.8)"
            s.backdropFilter = "blur(4px)"
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
                s.background = Theme.card
                s.borderLeft = "1px solid ${Theme.borderSoft}"
                s.zIndex = "1000"
                s.padding = "32px 30px 60px"
                s.boxShadow = "-20px 0 60px rgba(0,0,0,0.1)"
                s.animation = "${Theme.slideInRight} 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94)"
            }
            onClick = { it.stopPropagation() }

            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.justifyContent = "space-between"
                    s.alignItems = "center"
                    s.marginBottom = "24px"
                }
                div {
                    css {
                        val s = asDynamic()
                        s.fontSize = "18px"
                        s.fontWeight = "800"
                        s.color = Theme.pageText
                    }
                    +"Candidate Details"
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
    val (diveOpen, setDiveOpen) = useState(false)
    val d = props.detail
    val p = d.profile.profile
    val r = d.ranking
    val pct = (r.score.coerceIn(0.0, 1.0) * 100).toInt()
    val initials = (p.anonymizedName ?: d.profile.candidateId).split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

    val verdict = when {
        pct >= 80 -> "Strong Recommendation"
        pct >= 70 -> "Worth Interviewing"
        pct >= 60 -> "Potential Fit"
        else -> "Hard Reject"
    }

    val verdictColor = when {
        pct >= 80 -> Theme.green
        pct >= 70 -> Theme.blue
        pct >= 60 -> Theme.yellow
        else -> Theme.red
    }

    // Overview Card
    div {
        css {
            val s = asDynamic()
            s.display = "flex"
            s.alignItems = "center"
            s.gap = "20px"
            s.background = Theme.card
            s.border = "1px solid ${Theme.borderSoft}"
            s.borderRadius = "16px"
            s.padding = "24px"
            s.marginBottom = "16px"
        }
        div {
            css {
                val s = asDynamic()
                s.width = "80px"
                s.height = "80px"
                s.borderRadius = "50%"
                s.background = Theme.border
                s.display = "flex"
                s.alignItems = "center"
                s.justifyContent = "center"
                s.color = Theme.dim
                s.fontWeight = "600"
            }
            +initials
        }
        div {
            css {
                val s = asDynamic()
                s.flex = "1"
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "24px"
                    s.fontWeight = "800"
                    s.color = Theme.pageText
                    s.fontFamily = Theme.headingFont
                }
                +(p.anonymizedName ?: d.profile.candidateId)
            }
            div {
                css {
                    val s = asDynamic()
                    s.fontSize = "15px"
                    s.color = Theme.dim
                    s.marginTop = "4px"
                }
                +(p.currentTitle ?: "Software Engineer")
            }
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.gap = "12px"
                    s.alignItems = "center"
                    s.fontSize = "13px"
                    s.color = Theme.faint
                    s.marginTop = "8px"
                }
                span { +"${p.yearsOfExperience} Yrs" }
                span { +"${p.location ?: "Remote"}" }
                span {
                    css {
                        val s = asDynamic()
                        s.color = Theme.green
                        s.background = "${Theme.green}15"
                        s.padding = "2px 6px"
                        s.borderRadius = "4px"
                    }
                    +"${d.profile.signals.noticePeriodDays} Days"
                }
            }
        }
        div {
            css {
                val s = asDynamic()
                s.position = "relative"
                s.width = "72px"
                s.height = "72px"
                s.display = "flex"
                s.flexDirection = "column"
                s.alignItems = "center"
                s.justifyContent = "center"
            }
            CircularProgress { value = pct }
            div {
                css {
                    val s = asDynamic()
                    s.position = "absolute"
                    s.display = "flex"
                    s.flexDirection = "column"
                    s.alignItems = "center"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontSize = "20px"
                        s.fontWeight = "800"
                        s.color = Theme.pageText
                        s.lineHeight = "1"
                    }
                    +"$pct%"
                }
            }
        }
    }



    // AI Verdict Section
    div {
        css {
            val s = asDynamic()
            s.marginBottom = "24px"
        }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.alignItems = "center"
                s.gap = "12px"
                s.marginBottom = "12px"
            }
            span {
                css {
                    val s = asDynamic()
                    s.color = Theme.purple
                    s.fontWeight = "800"
                    s.fontSize = "14px"
                }
                +"AI Verdict"
            }
            span {
                css {
                    val s = asDynamic()
                    s.background = "${verdictColor}15"
                    s.color = verdictColor
                    s.padding = "4px 12px"
                    s.borderRadius = "999px"
                    s.fontSize = "12px"
                    s.fontWeight = "600"
                }
                +verdict
            }
        }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "14px"
                s.lineHeight = "1.6"
                s.color = Theme.pageText
            }
            +(r.reasoning ?: "Excellent match for the role. Strong backend skills, relevant experience, and good cultural fit.")
        }
    }

    // Score Breakdown
    SectionLabel { text = "Score Breakdown" }
    div {
        css {
            val s = asDynamic()
            s.display = "grid"
            s.gridTemplateColumns = "repeat(4, 1fr)"
            s.gap = "12px"
            s.marginBottom = "24px"
        }
        val factorsToDisplay = d.match.factors

        factorsToDisplay.forEach { f ->
            div {
                css {
                    val s = asDynamic()
                    s.background = Theme.card
                    s.border = "1px solid ${Theme.borderSoft}"
                    s.borderRadius = "8px"
                    s.padding = "16px 12px"
                    s.display = "flex"
                    s.flexDirection = "column"
                    s.alignItems = "center"
                    s.justifyContent = "center"
                    s.gap = "8px"
                }
                div {
                    css {
                        val s = asDynamic()
                        s.fontSize = "12px"
                        s.color = Theme.dim
                        s.textAlign = "center"
                    }
                    +f.label
                }
                div {
                    css {
                        val s = asDynamic()
                        s.fontSize = "20px"
                        s.fontWeight = "700"
                        s.color = Theme.fitColor(f.value)
                    }
                    +"${(f.value * 100).toInt()}%"
                }
                if (!f.detail.isNullOrBlank()) {
                    div {
                        css {
                            val s = asDynamic()
                            s.fontSize = "11px"
                            s.color = Theme.dim
                            s.textAlign = "center"
                        }
                        +f.detail!!
                    }
                }
            }
        }
    }

    if (!p.summary.isNullOrBlank()) {
        SectionLabel { text = "Summary" }
        div {
            css {
                val s = asDynamic()
                s.fontSize = "14px"
                s.lineHeight = "1.6"
                s.color = Theme.pageText
                s.marginBottom = "24px"
            }
            +p.summary!!
        }
    }

    if (d.match.matchedSkills.isNotEmpty()) {
        SectionLabel { text = "Matched Skills" }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.flexWrap = "wrap"
                s.gap = "8px"
                s.marginBottom = "24px"
            }
            d.match.matchedSkills.forEach { skill ->
                Chip { text = skill; strong = true }
            }
        }
    }

    if (d.profile.careerHistory.isNotEmpty()) {
        SectionLabel { text = "Experience" }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.flexDirection = "column"
                s.gap = "16px"
                s.marginBottom = "24px"
            }
            d.profile.careerHistory.forEach { job ->
                div {
                    css {
                        val s = asDynamic()
                        s.fontSize = "14px"
                        s.color = Theme.pageText
                    }
                    div {
                        css {
                            val s = asDynamic()
                            s.fontWeight = "600"
                        }
                        +(job.title ?: "Unknown Title")
                        +" · "
                        span {
                            css {
                                val s = asDynamic()
                                s.color = Theme.dim
                                s.fontWeight = "400"
                            }
                            +(job.company ?: "Unknown Company")
                        }
                    }
                    div {
                        css {
                            val s = asDynamic()
                            s.fontSize = "12px"
                            s.color = Theme.dim
                            s.marginTop = "4px"
                        }
                        +"${job.startDate ?: ""} - ${if(job.current) "Present" else (job.endDate ?: "")}"
                    }
                }
            }
        }
    }

    if (d.profile.skills.isNotEmpty()) {
        SectionLabel { text = "Top Skills" }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.flexWrap = "wrap"
                s.gap = "8px"
                s.marginBottom = "24px"
            }
            d.profile.skills.take(10).forEach { skill ->
                Chip { text = skill.name; strong = false }
            }
        }
    }

    if (d.profile.education.isNotEmpty()) {
        SectionLabel { text = "Education" }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.flexDirection = "column"
                s.gap = "12px"
                s.marginBottom = "24px"
            }
            d.profile.education.forEach { edu ->
                div {
                    css {
                        val s = asDynamic()
                        s.fontSize = "13px"
                        s.color = Theme.pageText
                    }
                    div {
                        css {
                            val s = asDynamic()
                            s.fontWeight = "600"
                        }
                        +(edu.institution ?: "Unknown Institution")
                    }
                    div {
                        css {
                            val s = asDynamic()
                            s.color = Theme.dim
                        }
                        +(edu.degree ?: "")
                        if (!edu.fieldOfStudy.isNullOrBlank()) {
                            +" in ${edu.fieldOfStudy}"
                        }
                    }
                }
            }
        }
    }

    // AI INTERVIEW COPILOT
    div {
        css {
            val s = asDynamic()
            s.background = Theme.card
            s.border = "1px solid ${Theme.borderSoft}"
            s.borderRadius = "16px"
            s.padding = "20px"
            s.marginTop = "32px"
        }
        div {
            css {
                val s = asDynamic()
                s.display = "flex"
                s.justifyContent = "space-between"
                s.alignItems = "center"
                s.marginBottom = "16px"
            }
            div {
                css {
                    val s = asDynamic()
                    s.display = "flex"
                    s.alignItems = "center"
                    s.gap = "8px"
                }
                span {
                    css {
                        val s = asDynamic()
                        s.fontSize = "14px"
                        s.fontWeight = "800"
                        s.color = Theme.purple
                        s.textTransform = "uppercase"
                        s.letterSpacing = "0.5px"
                    }
                    +"AI Interview Copilot"
                }
            }
        }
        
        if (!diveOpen) {
            button {
                css {
                    val s = asDynamic()
                    s.background = Theme.primary
                    s.border = "none"
                    s.color = "#ffffff"
                    s.borderRadius = "8px"
                    s.padding = "12px 24px"
                    s.fontSize = "14px"
                    s.cursor = "pointer"
                    s.fontWeight = "600"
                    s.width = "100%"
                    s.transition = "all 0.2s"
                    val hoverObj = js("{}")
                    hoverObj.background = Theme.primaryHover
                    s["&:hover"] = hoverObj
                }
                onClick = { setDiveOpen(true) }
                +"Generate Interview Prep & Deep Dive"
            }
        }

        if (diveOpen) {
            AiDeepDive { candidateId = d.profile.candidateId }
        }
    }
}
