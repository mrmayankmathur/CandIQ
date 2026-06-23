"""
Honeypot detection and disqualifier filtering.

Identifies synthetic/fake candidate profiles (honeypots) and applies
hard disqualification filters based on JD requirements. These run
after retrieval to remove unsuitable candidates before scoring.
"""

from __future__ import annotations

from ranker import config
from ranker.models.candidate import Candidate


def detect_honeypot(candidate: Candidate) -> tuple[bool, list[str]]:
    """
    Detect synthetic/fake candidate profiles (honeypots).

    Checks four heuristics for telltale signs of fabricated profiles:
    1. Expert skills with zero duration — synthetic profiles often claim
       expert-level skills without any usage history.
    2. Experience inconsistency — total career months diverge wildly from
       declared years of experience.
    3. Too many expert skills — real humans rarely have 10+ expert skills.
    4. Title-skill domain mismatch — non-tech title but suspiciously high
       number of JD-relevant technical skills.

    Args:
        candidate: Parsed Candidate object.

    Returns:
        tuple[bool, list[str]]: (is_honeypot, list_of_triggered_reasons).
    """
    reasons: list[str] = []

    # ── Check 1: Expert skills with 0 duration_months ────────────────────
    expert_no_duration = sum(
        1
        for s in candidate.skills
        if s.proficiency == "expert" and s.duration_months == 0
    )
    if expert_no_duration >= 3:
        reasons.append(
            f"expert_no_duration: {expert_no_duration} skills claim 'expert' "
            f"with 0 months duration"
        )

    # ── Check 2: Experience inconsistency ────────────────────────────────
    total_career_months = sum(
        entry.duration_months for entry in candidate.career_history
    )
    declared_years = candidate.profile.years_of_experience
    computed_years = total_career_months / 12.0 if total_career_months > 0 else 0.0
    if abs(computed_years - declared_years) > 3:
        reasons.append(
            f"experience_inconsistency: declared {declared_years:.1f} yrs but "
            f"career history sums to {computed_years:.1f} yrs "
            f"(delta {abs(computed_years - declared_years):.1f} yrs)"
        )

    # ── Check 3: Too many expert skills ──────────────────────────────────
    expert_count = sum(
        1 for s in candidate.skills if s.proficiency == "expert"
    )
    if expert_count >= 10:
        reasons.append(
            f"too_many_experts: {expert_count} skills at 'expert' level"
        )

    # ── Check 4: Title-skill domain mismatch ─────────────────────────────
    current_title_lower = candidate.profile.current_title.lower()
    is_non_tech_title = any(
        kw in current_title_lower for kw in config.NON_TECH_TITLE_KEYWORDS
    )
    if is_non_tech_title:
        # Count how many JD-relevant skills this non-tech person has
        candidate_skill_names = {s.name.lower() for s in candidate.skills}
        relevant_skills = candidate_skill_names & (
            config.JD_MUST_HAVE_SKILLS | config.JD_EXPANDED_SKILLS
        )
        # Threshold lowered from 8 → 3: a non-tech title with 3+ JD skills is a trap
        if len(relevant_skills) > 3:
            reasons.append(
                f"title_skill_mismatch: non-tech title '{candidate.profile.current_title}' "
                f"but has {len(relevant_skills)} JD-relevant technical skills "
                f"({', '.join(list(relevant_skills)[:5])})"
            )

    # ── Check 5: Clearly non-ML title with ANY must-have skills ──────────
    # A Mechanical Engineer, Graphic Designer, or Project Manager should NEVER
    # have sentence-transformers / FAISS / vector DB skills. Even 1 is a trap.
    is_clearly_non_ml = any(
        kw in current_title_lower for kw in config.CLEARLY_NON_ML_TITLES
    )
    if is_clearly_non_ml:
        candidate_skill_names = {s.name.lower() for s in candidate.skills}
        must_have_matches = candidate_skill_names & config.JD_MUST_HAVE_SKILLS
        if len(must_have_matches) >= 1:
            reasons.append(
                f"non_ml_title_with_ml_skills: clearly non-ML title "
                f"'{candidate.profile.current_title}' but has must-have skills: "
                f"{', '.join(must_have_matches)}"
            )

    return (len(reasons) > 0, reasons)


def check_disqualifiers(candidate: Candidate) -> tuple[bool, list[str]]:
    """
    Apply hard disqualification filters based on JD requirements.

    These represent firm "no" signals where the candidate fundamentally
    does not match what the JD is looking for.

    Checks:
    1. consulting_only — Entire career at consulting/services firms.
    2. non_tech_role — Current role is non-technical with no tech career history.
    3. pure_research — All roles are academic/research with no production work.
    4. insufficient_experience — Far below JD minimum experience requirement.

    Args:
        candidate: Parsed Candidate object.

    Returns:
        tuple[bool, list[str]]: (is_disqualified, list_of_triggered_reasons).
    """
    reasons: list[str] = []

    # ── Check 1: Consulting-only career ──────────────────────────────────
    if candidate.career_history:
        all_consulting = all(
            entry.company.lower() in config.CONSULTING_FIRMS
            for entry in candidate.career_history
        )
        if all_consulting:
            companies = [e.company for e in candidate.career_history]
            reasons.append(
                f"consulting_only: entire career at consulting firms "
                f"({', '.join(set(companies))})"
            )

    # ── Check 2: Non-tech role ───────────────────────────────────────────
    current_title_lower = candidate.profile.current_title.lower()
    is_non_tech = any(
        kw in current_title_lower for kw in config.NON_TECH_TITLE_KEYWORDS
    )
    if is_non_tech:
        # Check if ANY career history role is tech-related
        has_tech_career = False
        tech_indicators = {
            "engineer", "developer", "programmer", "scientist",
            "analyst", "architect", "devops", "sre", "data",
            "machine learning", "ml", "ai", "software", "backend",
            "frontend", "full stack", "fullstack",
        }
        for entry in candidate.career_history:
            title_lower = entry.title.lower()
            if any(ind in title_lower for ind in tech_indicators):
                has_tech_career = True
                break

        if not has_tech_career:
            reasons.append(
                f"non_tech_role: current title '{candidate.profile.current_title}' "
                f"is non-technical and no tech roles in career history"
            )

    # ── Check 3: Pure research (no production experience) ────────────────
    if candidate.career_history:
        all_research = all(
            any(
                rt in entry.title.lower()
                for rt in config.PURE_RESEARCH_TITLES
            )
            for entry in candidate.career_history
        )
        if all_research:
            # Check if any description mentions production/deployed work
            has_production = any(
                "production" in entry.description.lower()
                or "deployed" in entry.description.lower()
                for entry in candidate.career_history
            )
            if not has_production:
                reasons.append(
                    "pure_research: all career titles are research/academic "
                    "with no evidence of production/deployment work"
                )

    # ── Check 4: Insufficient experience ─────────────────────────────────
    if candidate.profile.years_of_experience < 2:
        reasons.append(
            f"insufficient_experience: {candidate.profile.years_of_experience:.1f} yrs "
            f"(JD minimum ~4-5 yrs, hard floor at 2 yrs)"
        )

    return (len(reasons) > 0, reasons)


def filter_candidates(
    candidate_ids: list[str],
    candidates: dict[str, Candidate],
) -> tuple[list[str], dict[str, list[str]]]:
    """
    Apply honeypot detection and disqualification filters to candidate list.

    Runs both filter stages on each candidate and separates them into
    passed (included in output) and rejected (logged with reasons).

    Args:
        candidate_ids: Ordered list of candidate IDs to filter.
        candidates: Dictionary mapping candidate_id → Candidate objects.

    Returns:
        tuple containing:
            - filtered_ids: List of candidate IDs that passed all filters.
            - rejection_log: Dict mapping rejected_id → list of reason strings.
    """
    filtered_ids: list[str] = []
    rejection_log: dict[str, list[str]] = {}

    for cid in candidate_ids:
        candidate = candidates.get(cid)
        if candidate is None:
            # Candidate data not available — skip but don't reject
            continue

        all_reasons: list[str] = []

        # Honeypot detection
        is_honeypot, honeypot_reasons = detect_honeypot(candidate)
        if is_honeypot:
            all_reasons.extend([f"[honeypot] {r}" for r in honeypot_reasons])

        # Hard disqualifiers
        is_disqualified, disq_reasons = check_disqualifiers(candidate)
        if is_disqualified:
            all_reasons.extend([f"[disqualifier] {r}" for r in disq_reasons])

        if all_reasons:
            rejection_log[cid] = all_reasons
        else:
            filtered_ids.append(cid)

    return filtered_ids, rejection_log
