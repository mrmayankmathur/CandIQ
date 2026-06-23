"""
Feature extraction for candidate scoring.

Extracts ~40 numerical features from each Candidate object, grouped into:
  - Skill match features (JD alignment)
  - Experience features (tenure, career span)
  - Career quality features (consulting vs product, production experience)
  - Behavioral signal features (activity, response, availability)
  - Education features (tier, degree relevance)
  - Location features (proximity preference)

All thresholds and skill sets are sourced from ranker.config.
"""

from __future__ import annotations

import re
from datetime import date, datetime
from pathlib import Path
from typing import Optional

import polars as pl
from tqdm import tqdm

from ranker import config
from ranker.models.candidate import Candidate
from ranker.utils.data_loader import stream_candidates

# ─── Pre-compile regex patterns for career description scanning ─────────────

_PRODUCTION_PATTERN = re.compile(
    r"\b(production|deployed|shipped|scaled|live|real users)\b",
    re.IGNORECASE,
)

_RANKING_SEARCH_PATTERN = re.compile(
    r"\b(ranking|search|retrieval|recommendation|matching|relevance|embedding)\b",
    re.IGNORECASE,
)

_ML_AI_TITLE_PATTERN = re.compile(
    r"\b(ml|machine learning|ai|data scientist|nlp|deep learning)\b",
    re.IGNORECASE,
)

_CS_FIELD_PATTERN = re.compile(
    r"(computer|software|information technology|data science|mathematics|statistics|electronics)",
    re.IGNORECASE,
)

_POSTGRAD_PATTERN = re.compile(
    r"\b(master|m\.tech|mtech|ms|phd|doctorate)\b",
    re.IGNORECASE,
)

# ─── Proficiency weight mapping ─────────────────────────────────────────────

_PROFICIENCY_WEIGHTS = {
    "expert": 4,
    "advanced": 3,
    "intermediate": 2,
    "beginner": 1,
}

# ─── Pre-lowered skill sets for fast lookup ─────────────────────────────────

_MUST_HAVE_LOWER = {s.lower() for s in config.JD_MUST_HAVE_SKILLS}
_NICE_TO_HAVE_LOWER = {s.lower() for s in config.JD_NICE_TO_HAVE_SKILLS}
_EXPANDED_LOWER = {s.lower() for s in config.JD_EXPANDED_SKILLS}
_ALL_JD_SKILLS = _MUST_HAVE_LOWER | _NICE_TO_HAVE_LOWER | _EXPANDED_LOWER

# Reference date for staleness calculations (hackathon snapshot)
_REFERENCE_DATE = date(2026, 6, 22)

# ─── Preferred location sets ────────────────────────────────────────────────

_PRIMARY_CITIES = {"pune", "noida"}
_SECONDARY_CITIES = {
    "bangalore", "bengaluru", "hyderabad", "mumbai", "delhi",
    "chennai", "kolkata", "gurugram", "gurgaon",
}

# ─── Non-tech title keywords (lowered) ─────────────────────────────────────

_NON_TECH_TITLES_LOWER = {t.lower() for t in config.NON_TECH_TITLE_KEYWORDS}


# ═══════════════════════════════════════════════════════════════════════════════
# Main Feature Extraction
# ═══════════════════════════════════════════════════════════════════════════════


def extract_features(candidate: Candidate) -> dict[str, float]:
    """
    Extract all numerical features from a single Candidate object.

    Returns a flat dictionary of feature_name → float, ready for
    conversion to a Polars/Pandas row or direct scoring.

    Args:
        candidate: Parsed Candidate object.

    Returns:
        dict[str, float]: ~40 features keyed by descriptive name.
    """
    features: dict[str, float] = {}

    # ── Skill Match Features ────────────────────────────────────────────
    _extract_skill_features(candidate, features)

    # ── Experience Features ─────────────────────────────────────────────
    _extract_experience_features(candidate, features)

    # ── Career Quality Features ─────────────────────────────────────────
    _extract_career_quality_features(candidate, features)

    # ── Behavioral Signal Features ──────────────────────────────────────
    _extract_behavioral_features(candidate, features)

    # ── Education Features ──────────────────────────────────────────────
    _extract_education_features(candidate, features)

    # ── Location Features ───────────────────────────────────────────────
    _extract_location_features(candidate, features)

    return features


# ═══════════════════════════════════════════════════════════════════════════════
# Feature Group Extractors
# ═══════════════════════════════════════════════════════════════════════════════


def _extract_skill_features(candidate: Candidate, features: dict[str, float]) -> None:
    """Compute skill match features against JD skill sets."""
    candidate_skill_names_lower = [s.name.lower() for s in candidate.skills]

    # Counts per JD skill category
    must_have_matches = set()
    nice_to_have_matches = set()
    expanded_matches = set()
    all_relevant_indices: set[int] = set()

    for i, skill_lower in enumerate(candidate_skill_names_lower):
        if skill_lower in _MUST_HAVE_LOWER:
            must_have_matches.add(skill_lower)
            all_relevant_indices.add(i)
        if skill_lower in _NICE_TO_HAVE_LOWER:
            nice_to_have_matches.add(skill_lower)
            all_relevant_indices.add(i)
        if skill_lower in _EXPANDED_LOWER:
            expanded_matches.add(skill_lower)
            all_relevant_indices.add(i)

    features["must_have_skill_count"] = float(len(must_have_matches))
    features["nice_to_have_skill_count"] = float(len(nice_to_have_matches))
    features["expanded_skill_count"] = float(len(expanded_matches))

    # Deduplicated total
    all_relevant_names = must_have_matches | nice_to_have_matches | expanded_matches
    features["total_relevant_skills"] = float(len(all_relevant_names))

    # Weighted skill score: proficiency weight × endorsement factor
    weighted_score = 0.0
    for idx in all_relevant_indices:
        skill = candidate.skills[idx]
        prof_weight = _PROFICIENCY_WEIGHTS.get(skill.proficiency.lower(), 1)
        endorsement_factor = min(skill.endorsements / 10.0, 2.0) + 1.0
        weighted_score += prof_weight * endorsement_factor
    features["weighted_skill_score"] = weighted_score

    # Average skill assessment for relevant skills
    assessment_scores = candidate.redrob_signals.skill_assessment_scores
    relevant_assessments = []
    for name in all_relevant_names:
        # Try exact match and original-cased names
        for key, val in assessment_scores.items():
            if key.lower() == name:
                relevant_assessments.append(val)
                break
    features["avg_skill_assessment"] = (
        sum(relevant_assessments) / len(relevant_assessments)
        if relevant_assessments
        else 0.0
    )

    # Average duration_months for relevant skills
    relevant_durations = [candidate.skills[i].duration_months for i in all_relevant_indices]
    features["skill_duration_months_avg"] = (
        sum(relevant_durations) / len(relevant_durations)
        if relevant_durations
        else 0.0
    )


def _extract_experience_features(candidate: Candidate, features: dict[str, float]) -> None:
    """Compute experience-related features."""
    yoe = candidate.profile.years_of_experience
    features["years_of_experience"] = float(yoe)

    # Experience in ideal range (JD: 5-9 ideal, 4-12 acceptable)
    if config.EXPERIENCE_IDEAL_MIN <= yoe <= config.EXPERIENCE_IDEAL_MAX:
        features["experience_in_ideal_range"] = 1.0
    elif config.EXPERIENCE_MIN <= yoe <= config.EXPERIENCE_MAX:
        features["experience_in_ideal_range"] = 0.7
    else:
        features["experience_in_ideal_range"] = 0.3

    # Career history aggregates
    career = candidate.career_history
    total_months = sum(c.duration_months for c in career)
    features["total_career_months"] = float(total_months)
    features["num_roles"] = float(len(career))

    # Current role duration
    current_duration = 0.0
    for entry in career:
        if entry.is_current:
            current_duration = float(entry.duration_months)
            break
    features["current_role_duration_months"] = current_duration


def _extract_career_quality_features(candidate: Candidate, features: dict[str, float]) -> None:
    """Compute career quality / trajectory features."""
    career = candidate.career_history
    num_roles = len(career)

    if num_roles == 0:
        features["product_company_ratio"] = 0.0
        features["consulting_only"] = 1.0
        features["avg_tenure_months"] = 0.0
        features["is_title_chaser"] = 0.0
        features["has_production_experience"] = 0.0
        features["has_ranking_search_exp"] = 0.0
        features["has_ml_ai_career"] = 0.0
        features["title_skill_mismatch"] = 0.0
        return

    # Consulting firm detection
    consulting_count = 0
    for entry in career:
        if entry.company.lower().strip() in config.CONSULTING_FIRMS:
            consulting_count += 1

    product_count = num_roles - consulting_count
    features["product_company_ratio"] = product_count / num_roles
    features["consulting_only"] = 1.0 if consulting_count == num_roles else 0.0

    # Average tenure
    total_months = sum(c.duration_months for c in career)
    avg_tenure = total_months / num_roles
    features["avg_tenure_months"] = avg_tenure

    # Title chaser: short avg tenure + many roles
    features["is_title_chaser"] = (
        1.0 if avg_tenure <= 18 and num_roles >= 3 else 0.0
    )

    # Scan career descriptions for domain signals
    has_production = False
    has_ranking_search = False
    has_ml_ai = False

    for entry in career:
        desc = entry.description
        title = entry.title
        if not has_production and _PRODUCTION_PATTERN.search(desc):
            has_production = True
        if not has_ranking_search and _RANKING_SEARCH_PATTERN.search(desc):
            has_ranking_search = True
        if not has_ml_ai and _ML_AI_TITLE_PATTERN.search(title):
            has_ml_ai = True

    features["has_production_experience"] = 1.0 if has_production else 0.0
    features["has_ranking_search_exp"] = 1.0 if has_ranking_search else 0.0
    features["has_ml_ai_career"] = 1.0 if has_ml_ai else 0.0

    # Title-skill mismatch TRAP detector
    current_title_lower = candidate.profile.current_title.lower()
    is_non_tech_title = any(kw in current_title_lower for kw in _NON_TECH_TITLES_LOWER)

    if is_non_tech_title:
        # Count tech skills from JD must-have + expanded
        tech_skill_set = _MUST_HAVE_LOWER | _EXPANDED_LOWER
        tech_skill_count = sum(
            1 for s in candidate.skills if s.name.lower() in tech_skill_set
        )
        features["title_skill_mismatch"] = 1.0 if tech_skill_count > 5 else 0.0
    else:
        features["title_skill_mismatch"] = 0.0


def _extract_behavioral_features(candidate: Candidate, features: dict[str, float]) -> None:
    """Compute behavioral / activity signal features."""
    signals = candidate.redrob_signals

    # Days since last active
    try:
        last_active = datetime.strptime(signals.last_active_date, "%Y-%m-%d").date()
        days_since = (_REFERENCE_DATE - last_active).days
    except (ValueError, TypeError):
        days_since = 365  # Fallback: assume stale

    features["days_since_last_active"] = float(max(0, days_since))

    # Recently active tiers
    if days_since <= config.STALENESS_MODERATE_DAYS:
        features["is_recently_active"] = 1.0
    elif days_since <= config.STALENESS_THRESHOLD_DAYS:
        features["is_recently_active"] = 0.5
    else:
        features["is_recently_active"] = 0.0

    # Direct flags
    features["is_open_to_work"] = 1.0 if signals.open_to_work_flag else 0.0
    features["recruiter_response_rate"] = float(signals.recruiter_response_rate)

    # Response time score
    rt = signals.avg_response_time_hours
    if rt <= 24:
        features["response_time_score"] = 1.0
    elif rt <= 72:
        features["response_time_score"] = 0.5
    else:
        features["response_time_score"] = 0.0

    # GitHub activity (normalize -1 → 0)
    features["github_activity_normalized"] = max(0.0, signals.github_activity_score) / 100.0

    # Direct rates
    features["interview_completion_rate"] = float(signals.interview_completion_rate)
    features["offer_acceptance_rate_normalized"] = max(0.0, float(signals.offer_acceptance_rate))

    # Notice period score
    np_days = signals.notice_period_days
    if np_days <= config.NOTICE_PERIOD_IDEAL:
        features["notice_period_score"] = 1.0
    elif np_days <= config.NOTICE_PERIOD_OK:
        features["notice_period_score"] = 0.7
    elif np_days <= config.NOTICE_PERIOD_HIGH:
        features["notice_period_score"] = 0.3
    else:
        features["notice_period_score"] = 0.0

    # Profile completeness (0-100 → 0-1)
    features["profile_completeness"] = signals.profile_completeness_score / 100.0

    # Engagement score (composite, capped at 1.0)
    engagement_raw = (
        signals.profile_views_received_30d
        + signals.applications_submitted_30d
        + signals.search_appearance_30d
        + signals.saved_by_recruiters_30d
    ) / 40.0
    features["engagement_score"] = min(engagement_raw, 1.0)

    # Verification score
    verified_count = (
        (1.0 if signals.verified_email else 0.0)
        + (1.0 if signals.verified_phone else 0.0)
        + (1.0 if signals.linkedin_connected else 0.0)
    )
    features["verification_score"] = verified_count / 3.0


def _extract_education_features(candidate: Candidate, features: dict[str, float]) -> None:
    """Compute education-related features."""
    education = candidate.education

    if not education:
        features["education_tier_score"] = 0.3
        features["has_cs_degree"] = 0.0
        features["has_postgrad"] = 0.0
        return

    # Tier scoring — pick the best tier
    tier_scores = {
        "tier_1": 1.0,
        "tier_2": 0.75,
        "tier_3": 0.5,
        "tier_4": 0.25,
        "unknown": 0.3,
    }
    best_tier = max(tier_scores.get(e.tier, 0.3) for e in education)
    features["education_tier_score"] = best_tier

    # CS-adjacent degree
    has_cs = any(_CS_FIELD_PATTERN.search(e.field_of_study) for e in education)
    features["has_cs_degree"] = 1.0 if has_cs else 0.0

    # Postgraduate degree
    has_postgrad = any(_POSTGRAD_PATTERN.search(e.degree) for e in education)
    features["has_postgrad"] = 1.0 if has_postgrad else 0.0


def _extract_location_features(candidate: Candidate, features: dict[str, float]) -> None:
    """Compute location fit features."""
    loc_lower = candidate.profile.location.lower()
    country_lower = candidate.profile.country.lower()
    combined = f"{loc_lower} {country_lower}"

    # Location scoring with city tier preference
    if any(city in combined for city in _PRIMARY_CITIES):
        features["location_score"] = 1.0
    elif any(city in combined for city in _SECONDARY_CITIES):
        features["location_score"] = 0.8
    elif "india" in combined:
        features["location_score"] = 0.6
    elif candidate.redrob_signals.willing_to_relocate:
        features["location_score"] = 0.3
    else:
        features["location_score"] = 0.1

    # Work mode fit
    work_mode = candidate.redrob_signals.preferred_work_mode.lower()
    if work_mode in ("hybrid", "flexible", "onsite"):
        features["work_mode_fit"] = 1.0
    elif work_mode == "remote":
        features["work_mode_fit"] = 0.6
    else:
        features["work_mode_fit"] = 0.5  # Unknown → neutral


# ═══════════════════════════════════════════════════════════════════════════════
# Batch Extraction → Parquet
# ═══════════════════════════════════════════════════════════════════════════════


def extract_all_features(
    filepath: Path = config.CANDIDATES_JSONL,
    output_path: Path = config.FEATURES_FILE,
) -> pl.DataFrame:
    """
    Stream through all candidates, extract features, and save as Parquet.

    Args:
        filepath: Path to candidates.jsonl.
        output_path: Destination for the Parquet file.

    Returns:
        pl.DataFrame: Features DataFrame (candidate_id + all feature columns).
    """
    rows: list[dict] = []

    for candidate in stream_candidates(filepath, show_progress=True):
        feat = extract_features(candidate)
        feat["candidate_id"] = candidate.candidate_id
        rows.append(feat)

    df = pl.DataFrame(rows)

    # Ensure output directory exists
    output_path.parent.mkdir(parents=True, exist_ok=True)
    df.write_parquet(output_path)

    print(f"\n✅ Saved {len(df)} candidate features → {output_path}")
    print(f"   Columns: {df.columns}")
    print(f"   Shape: {df.shape}")

    return df


# ═══════════════════════════════════════════════════════════════════════════════
# CLI Entry Point
# ═══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 70)
    print("  Intelligent Candidate Discovery — Feature Extraction")
    print("=" * 70)
    print(f"  Input:  {config.CANDIDATES_JSONL}")
    print(f"  Output: {config.FEATURES_FILE}")
    print()

    df = extract_all_features()

    # Print quick stats
    print("\n📊 Feature Statistics:")
    print(df.describe())
