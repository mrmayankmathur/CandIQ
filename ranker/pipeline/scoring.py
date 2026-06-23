"""
Final candidate scoring using pre-computed features.

Combines multiple signal categories into a weighted final score:
- Skill match (25%) — alignment with JD required/expanded skills
- Career quality (20%) — product companies, tenure, production experience
- Experience fit (15%) — years in ideal range, ML/AI career presence
- Behavioral signals (15%) — activity, response rate, availability
- Semantic similarity (10%) — embedding-based JD-candidate similarity
- Education (5%) — institution tier, degree relevance
- Location fit (5%) — geographic/work-mode alignment
- Keyword relevance (5%) — BM25-based keyword overlap
"""

from __future__ import annotations

from ranker import config


def normalize(value: float, max_val: float) -> float:
    """
    Normalize a value to [0, 1] range by dividing by max_val, clamped at 1.0.

    Args:
        value: The raw value to normalize.
        max_val: The maximum expected value (maps to 1.0).

    Returns:
        float: Normalized value in [0.0, 1.0].
    """
    if max_val <= 0:
        return 0.0
    return min(value / max_val, 1.0)


def compute_final_score(
    features: dict[str, float],
    retrieval_score: float,
) -> float:
    """
    Compute the weighted final score for a candidate.

    Combines pre-computed features into 8 scoring components, each
    an internal weighted blend of sub-signals, then applies
    config.SCORE_WEIGHTS to produce the final score.

    Args:
        features: Pre-computed feature dict for the candidate. Expected keys:
            - weighted_skill_score, must_have_skill_count
            - experience_in_ideal_range, has_ml_ai_career
            - product_company_ratio, has_production_experience,
              has_ranking_search_exp, is_title_chaser, avg_tenure_months
            - is_recently_active, recruiter_response_rate, is_open_to_work,
              notice_period_score, engagement_score,
              github_activity_normalized, verification_score
            - education_tier_score, has_cs_degree, has_postgrad
            - location_score, work_mode_fit
            - keyword_relevance (optional)
        retrieval_score: RRF or semantic similarity score from retrieval stage.

    Returns:
        float: Final weighted score (higher is better).
    """
    weights = config.SCORE_WEIGHTS

    # ── Skill Match (25%) ────────────────────────────────────────────────
    skill_match = (
        normalize(features.get("weighted_skill_score", 0), max_val=50) * 0.7
        + normalize(features.get("must_have_skill_count", 0), max_val=10) * 0.3
    )

    # ── Experience Fit (15%) ─────────────────────────────────────────────
    experience_fit = (
        features.get("experience_in_ideal_range", 0) * 0.6
        + features.get("has_ml_ai_career", 0) * 0.4
    )

    # ── Career Quality (20%) ─────────────────────────────────────────────
    career_quality = (
        features.get("product_company_ratio", 0) * 0.3
        + features.get("has_production_experience", 0) * 0.25
        + features.get("has_ranking_search_exp", 0) * 0.25
        + (1 - features.get("is_title_chaser", 0)) * 0.1
        + normalize(features.get("avg_tenure_months", 0), max_val=48) * 0.1
    )

    # ── Behavioral Signals (15%) ─────────────────────────────────────────
    behavioral_signals = (
        features.get("is_recently_active", 0) * 0.2
        + features.get("recruiter_response_rate", 0) * 0.2
        + features.get("is_open_to_work", 0) * 0.15
        + features.get("notice_period_score", 0) * 0.15
        + features.get("engagement_score", 0) * 0.1
        + features.get("github_activity_normalized", 0) * 0.1
        + features.get("verification_score", 0) * 0.1
    )

    # ── Education (5%) ───────────────────────────────────────────────────
    education = (
        features.get("education_tier_score", 0) * 0.5
        + features.get("has_cs_degree", 0) * 0.3
        + features.get("has_postgrad", 0) * 0.2
    )

    # ── Location Fit (5%) ────────────────────────────────────────────────
    location_fit = (
        features.get("location_score", 0) * 0.7
        + features.get("work_mode_fit", 0) * 0.3
    )

    # ── Semantic Similarity (10%) ────────────────────────────────────────
    semantic_similarity = retrieval_score

    # ── Keyword Relevance (5%) ───────────────────────────────────────────
    keyword_relevance = features.get("keyword_relevance", 0)

    # ── Final weighted combination ───────────────────────────────────────
    components = {
        "skill_match": skill_match,
        "experience_fit": experience_fit,
        "career_quality": career_quality,
        "behavioral_signals": behavioral_signals,
        "education": education,
        "location_fit": location_fit,
        "semantic_similarity": semantic_similarity,
        "keyword_relevance": keyword_relevance,
    }

    final_score = sum(
        components[key] * weights[key] for key in weights if key in components
    )

    return final_score


def rank_candidates(
    candidate_features: dict[str, dict],
    retrieval_scores: dict[str, float],
) -> list[tuple[str, float]]:
    """
    Score and rank all candidates using pre-computed features.

    Applies compute_final_score to each candidate and returns
    a descending-sorted ranked list.

    Raw RRF scores are tiny (≈1/(k+rank), max ~0.016) and not comparable
    to the other [0, 1] scoring components, so they are min-max normalized
    across the candidate set before being fed in as semantic similarity.

    Args:
        candidate_features: Dict mapping candidate_id → feature dict.
        retrieval_scores: Dict mapping candidate_id → retrieval score (RRF).

    Returns:
        list[tuple[str, float]]: Ranked list of (candidate_id, final_score),
            sorted by score descending.
    """
    # Min-max normalize retrieval scores to [0, 1] over the candidate set so
    # the semantic_similarity component is on the same scale as the others.
    norm_retrieval = _normalize_scores(retrieval_scores)

    scored: list[tuple[str, float]] = []

    for cid, features in candidate_features.items():
        retrieval_score = norm_retrieval.get(cid, 0.0)
        score = compute_final_score(features, retrieval_score)
        scored.append((cid, score))

    # Sort by score descending, tie-break by candidate_id ascending so the
    # ordering is deterministic and matches the submission tie-break rule.
    scored.sort(key=lambda x: (-x[1], x[0]))
    return scored


def _normalize_scores(scores: dict[str, float]) -> dict[str, float]:
    """Min-max normalize a dict of scores to [0, 1]. Empty/flat → all 0."""
    if not scores:
        return {}
    values = scores.values()
    lo = min(values)
    hi = max(values)
    span = hi - lo
    if span <= 0:
        return {cid: 0.0 for cid in scores}
    return {cid: (val - lo) / span for cid, val in scores.items()}
