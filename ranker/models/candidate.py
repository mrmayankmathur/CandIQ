"""
Data models for candidate profiles.

Mirrors the candidate_schema.json from the hackathon dataset.
Uses dataclasses for lightweight, fast construction from JSON.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Optional


@dataclass(slots=True)
class CareerEntry:
    """A single entry in a candidate's career history."""
    company: str
    title: str
    start_date: str
    end_date: Optional[str]        # None if current role
    duration_months: int
    is_current: bool
    industry: str
    company_size: str              # "1-10", "11-50", ..., "10001+"
    description: str


@dataclass(slots=True)
class Education:
    """A single education entry."""
    institution: str
    degree: str
    field_of_study: str
    start_year: int
    end_year: int
    grade: Optional[str] = None     # GPA, percentage, or class
    tier: str = "unknown"           # tier_1, tier_2, tier_3, tier_4, unknown


@dataclass(slots=True)
class Skill:
    """A skill with proficiency and metadata."""
    name: str
    proficiency: str               # beginner, intermediate, advanced, expert
    endorsements: int = 0
    duration_months: int = 0


@dataclass(slots=True)
class Certification:
    """A professional certification."""
    name: str
    issuer: str
    year: int


@dataclass(slots=True)
class Language:
    """Language proficiency."""
    language: str
    proficiency: str               # basic, conversational, professional, native


@dataclass(slots=True)
class SalaryRange:
    """Expected salary range in INR LPA."""
    min: float
    max: float


@dataclass(slots=True)
class RedrobSignals:
    """
    23 behavioral signals from the Redrob platform.
    These are crucial for ranking — JD explicitly says to weigh
    behavioral signals for availability and engagement.
    """
    profile_completeness_score: float         # 0-100
    signup_date: str
    last_active_date: str
    open_to_work_flag: bool
    profile_views_received_30d: int
    applications_submitted_30d: int
    recruiter_response_rate: float            # 0.0-1.0
    avg_response_time_hours: float
    skill_assessment_scores: dict[str, float]  # skill_name → 0-100
    connection_count: int
    endorsements_received: int
    notice_period_days: int                    # 0-180
    expected_salary_range: SalaryRange
    preferred_work_mode: str                   # remote, hybrid, onsite, flexible
    willing_to_relocate: bool
    github_activity_score: float               # -1 to 100 (-1 = no GitHub)
    search_appearance_30d: int
    saved_by_recruiters_30d: int
    interview_completion_rate: float           # 0.0-1.0
    offer_acceptance_rate: float               # -1 to 1.0 (-1 = no history)
    verified_email: bool
    verified_phone: bool
    linkedin_connected: bool


@dataclass(slots=True)
class Profile:
    """Basic professional profile information."""
    anonymized_name: str
    headline: str
    summary: str
    location: str
    country: str
    years_of_experience: float
    current_title: str
    current_company: str
    current_company_size: str
    current_industry: str


@dataclass(slots=True)
class Candidate:
    """
    Complete candidate profile, matching candidate_schema.json.

    This is the primary data structure used throughout the pipeline.
    """
    candidate_id: str              # CAND_XXXXXXX format
    profile: Profile
    career_history: list[CareerEntry]
    education: list[Education]
    skills: list[Skill]
    redrob_signals: RedrobSignals
    certifications: list[Certification] = field(default_factory=list)
    languages: list[Language] = field(default_factory=list)

    @staticmethod
    def from_dict(data: dict) -> Candidate:
        """
        Parse a candidate from a JSON dictionary.
        Optimized for speed — called 100K times.
        """
        p = data["profile"]
        profile = Profile(
            anonymized_name=p["anonymized_name"],
            headline=p["headline"],
            summary=p["summary"],
            location=p["location"],
            country=p["country"],
            years_of_experience=p["years_of_experience"],
            current_title=p["current_title"],
            current_company=p["current_company"],
            current_company_size=p["current_company_size"],
            current_industry=p["current_industry"],
        )

        career_history = [
            CareerEntry(
                company=c["company"],
                title=c["title"],
                start_date=c["start_date"],
                end_date=c.get("end_date"),
                duration_months=c["duration_months"],
                is_current=c["is_current"],
                industry=c["industry"],
                company_size=c["company_size"],
                description=c["description"],
            )
            for c in data["career_history"]
        ]

        education = [
            Education(
                institution=e["institution"],
                degree=e["degree"],
                field_of_study=e["field_of_study"],
                start_year=e["start_year"],
                end_year=e["end_year"],
                grade=e.get("grade"),
                tier=e.get("tier", "unknown"),
            )
            for e in data.get("education", [])
        ]

        skills = [
            Skill(
                name=s["name"],
                proficiency=s["proficiency"],
                endorsements=s.get("endorsements", 0),
                duration_months=s.get("duration_months", 0),
            )
            for s in data.get("skills", [])
        ]

        sig = data["redrob_signals"]
        salary_data = sig.get("expected_salary_range_inr_lpa", {})
        signals = RedrobSignals(
            profile_completeness_score=sig["profile_completeness_score"],
            signup_date=sig["signup_date"],
            last_active_date=sig["last_active_date"],
            open_to_work_flag=sig["open_to_work_flag"],
            profile_views_received_30d=sig["profile_views_received_30d"],
            applications_submitted_30d=sig["applications_submitted_30d"],
            recruiter_response_rate=sig["recruiter_response_rate"],
            avg_response_time_hours=sig["avg_response_time_hours"],
            skill_assessment_scores=sig.get("skill_assessment_scores", {}),
            connection_count=sig["connection_count"],
            endorsements_received=sig["endorsements_received"],
            notice_period_days=sig["notice_period_days"],
            expected_salary_range=SalaryRange(
                min=salary_data.get("min", 0),
                max=salary_data.get("max", 0),
            ),
            preferred_work_mode=sig["preferred_work_mode"],
            willing_to_relocate=sig["willing_to_relocate"],
            github_activity_score=sig["github_activity_score"],
            search_appearance_30d=sig["search_appearance_30d"],
            saved_by_recruiters_30d=sig["saved_by_recruiters_30d"],
            interview_completion_rate=sig["interview_completion_rate"],
            offer_acceptance_rate=sig["offer_acceptance_rate"],
            verified_email=sig["verified_email"],
            verified_phone=sig["verified_phone"],
            linkedin_connected=sig["linkedin_connected"],
        )

        certifications = [
            Certification(name=c["name"], issuer=c["issuer"], year=c["year"])
            for c in data.get("certifications", [])
        ]

        languages = [
            Language(language=l["language"], proficiency=l["proficiency"])
            for l in data.get("languages", [])
        ]

        return Candidate(
            candidate_id=data["candidate_id"],
            profile=profile,
            career_history=career_history,
            education=education,
            skills=skills,
            redrob_signals=signals,
            certifications=certifications,
            languages=languages,
        )
