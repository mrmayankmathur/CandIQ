"""
Template-based reasoning generation for top-ranked candidates.

Generates concise, specific justification strings for the top-100 candidates.
Each reasoning references actual candidate data (skills, career, behavioral
signals) rather than generic templates, making each one unique and auditable.
"""

from __future__ import annotations

from ranker import config
from ranker.models.candidate import Candidate


def _get_matching_skills(candidate: Candidate, top_n: int = 3) -> list[str]:
    """
    Find the candidate's skills that match JD requirements.

    Prioritizes must-have skills, then expanded skills.

    Args:
        candidate: Parsed Candidate object.
        top_n: Maximum number of matching skills to return.

    Returns:
        list[str]: Top matching skill names.
    """
    must_have_matches: list[str] = []
    expanded_matches: list[str] = []

    for skill in candidate.skills:
        name_lower = skill.name.lower()
        if name_lower in config.JD_MUST_HAVE_SKILLS:
            must_have_matches.append(skill.name)
        elif name_lower in config.JD_EXPANDED_SKILLS:
            expanded_matches.append(skill.name)

    # Must-have skills first, then expanded
    combined = must_have_matches + expanded_matches
    return combined[:top_n]


def _get_career_highlight(candidate: Candidate) -> str | None:
    """
    Extract the most notable career highlight for the reasoning.

    Looks for production experience, product company roles, or
    domain-specific experience in search/ranking/ML.

    Args:
        candidate: Parsed Candidate object.

    Returns:
        str or None: A short career highlight string, or None.
    """
    for entry in candidate.career_history:
        desc_lower = entry.description.lower()
        title_lower = entry.title.lower()

        # Production ML/search experience is the strongest signal
        if any(
            kw in desc_lower
            for kw in ("production", "deployed", "serving", "inference")
        ):
            return f"production experience at {entry.company}"

        # Ranking/search/recommendation domain
        if any(
            kw in desc_lower or kw in title_lower
            for kw in ("ranking", "search", "retrieval", "recommendation")
        ):
            return f"ranking/search experience at {entry.company}"

    # Fallback: mention current company if it's a product company
    company_lower = candidate.profile.current_company.lower()
    if company_lower not in config.CONSULTING_FIRMS:
        return f"currently at {candidate.profile.current_company}"

    return None


def _get_behavioral_note(
    candidate: Candidate,
    features: dict[str, float],
) -> str | None:
    """
    Generate a short behavioral signal note.

    Mentions response rate, activity status, or open-to-work flag.

    Args:
        candidate: Parsed Candidate object.
        features: Pre-computed feature dict.

    Returns:
        str or None: A short behavioral note, or None.
    """
    parts: list[str] = []

    response_rate = candidate.redrob_signals.recruiter_response_rate
    if response_rate >= config.RESPONSE_RATE_GOOD:
        parts.append(f"response rate {response_rate:.2f}")

    if candidate.redrob_signals.open_to_work_flag:
        parts.append("open to work")

    if features.get("is_recently_active", 0) > 0.5:
        parts.append("recently active")

    if parts:
        return ", ".join(parts)
    return None


def _get_concerns(
    candidate: Candidate,
    features: dict[str, float],
) -> list[str]:
    """
    Identify any concerns worth mentioning in the reasoning.

    Flags high notice periods, location issues, short tenure, or
    missing expected skills.

    Args:
        candidate: Parsed Candidate object.
        features: Pre-computed feature dict.

    Returns:
        list[str]: List of concern strings (may be empty).
    """
    concerns: list[str] = []

    # High notice period
    notice_days = candidate.redrob_signals.notice_period_days
    if notice_days > config.NOTICE_PERIOD_HIGH:
        concerns.append(f"notice period {notice_days} days")

    # Location concern
    location_score = features.get("location_score", 1.0)
    if location_score < 0.5:
        concerns.append(
            f"location mismatch ({candidate.profile.location})"
        )

    # Short average tenure
    avg_tenure = features.get("avg_tenure_months", 36)
    if avg_tenure < 12:
        concerns.append(f"short avg tenure ({avg_tenure:.0f} months)")

    # Low response rate
    response_rate = candidate.redrob_signals.recruiter_response_rate
    if response_rate < config.RESPONSE_RATE_BAD:
        concerns.append(f"low response rate ({response_rate:.2f})")

    # Check for missing critical skills
    candidate_skill_names = {s.name.lower() for s in candidate.skills}
    critical_missing = []
    for skill_kw in ("evaluation framework", "ndcg", "a/b testing"):
        if skill_kw not in candidate_skill_names:
            critical_missing.append(skill_kw)
    if critical_missing:
        concerns.append(
            f"no {' / '.join(critical_missing)} experience mentioned"
        )

    return concerns


def generate_reasoning(
    candidate: Candidate,
    features: dict[str, float],
    score: float,
    rank: int,
) -> str:
    """
    Generate a concise 1-2 sentence reasoning for a ranked candidate.

    Each reasoning is unique, built from actual candidate data:
    - Current title + years of experience
    - Top 3 matching skills
    - Career highlight (production exp, product company)
    - Behavioral signals (response rate, activity)
    - Concerns if any (notice period, location, tenure)

    Args:
        candidate: Parsed Candidate object.
        features: Pre-computed feature dict.
        score: Final computed score.
        rank: Candidate's rank (1-based).

    Returns:
        str: Reasoning string, e.g.:
            'Senior ML Engineer with 7.2 yrs; production embeddings + FAISS
            experience at Flipkart; response rate 0.82, open to work;
            notice period 30 days. Minor concern: no evaluation framework
            experience mentioned.'
    """
    parts: list[str] = []

    # ── Title + experience ───────────────────────────────────────────────
    title = candidate.profile.current_title
    yrs = candidate.profile.years_of_experience
    parts.append(f"{title} with {yrs:.1f} yrs")

    # ── Top matching skills ──────────────────────────────────────────────
    matching_skills = _get_matching_skills(candidate, top_n=3)
    if matching_skills:
        skills_str = " + ".join(matching_skills)
        parts.append(skills_str)

    # ── Career highlight ─────────────────────────────────────────────────
    highlight = _get_career_highlight(candidate)
    if highlight:
        parts.append(highlight)

    # ── Behavioral note ──────────────────────────────────────────────────
    behavioral = _get_behavioral_note(candidate, features)
    if behavioral:
        parts.append(behavioral)

    # ── Notice period (always mention if available) ──────────────────────
    notice_days = candidate.redrob_signals.notice_period_days
    if notice_days > 0:
        parts.append(f"notice period {notice_days} days")

    # Build the main reasoning sentence
    reasoning = "; ".join(parts) + "."

    # ── Concerns ─────────────────────────────────────────────────────────
    concerns = _get_concerns(candidate, features)
    if concerns:
        concerns_str = "; ".join(concerns)
        reasoning += f" Minor concern: {concerns_str}."

    return reasoning


def generate_all_reasonings(
    ranked_candidates: list[tuple[str, float]],
    candidates: dict[str, Candidate],
    features: dict[str, dict],
) -> list[tuple[str, int, float, str]]:
    """
    Generate reasonings for the top-100 ranked candidates.

    Iterates through the ranked list and produces a unique reasoning
    for each candidate using their actual data.

    Args:
        ranked_candidates: Ranked list of (candidate_id, score) from scoring.
        candidates: Dict mapping candidate_id → Candidate objects.
        features: Dict mapping candidate_id → pre-computed feature dict.

    Returns:
        list[tuple[str, int, float, str]]: List of
            (candidate_id, rank, score, reasoning) for the top 100.
    """
    results: list[tuple[str, int, float, str]] = []

    top_n = min(len(ranked_candidates), config.TOP_K_OUTPUT)
    for rank_idx, (cid, score) in enumerate(ranked_candidates[:top_n]):
        rank = rank_idx + 1  # 1-based rank

        candidate = candidates.get(cid)
        candidate_features = features.get(cid, {})

        if candidate is None:
            # Candidate data not loaded — generate minimal reasoning
            reasoning = f"Rank {rank}, score {score:.4f}. Candidate data unavailable."
        else:
            reasoning = generate_reasoning(
                candidate, candidate_features, score, rank
            )

        results.append((cid, rank, score, reasoning))

    return results
