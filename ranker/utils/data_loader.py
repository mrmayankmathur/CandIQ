"""
Data loader for candidates.jsonl.

Provides streaming and batch loading of candidate data,
optimized for memory efficiency (487MB file, 100K records, 16GB RAM limit).
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Generator

import orjson
from tqdm import tqdm

from ranker.models.candidate import Candidate
from ranker import config


def stream_candidates_raw(
    filepath: Path = config.CANDIDATES_JSONL,
    show_progress: bool = True,
) -> Generator[dict, None, None]:
    """
    Stream raw JSON dicts from candidates.jsonl one at a time.

    Memory efficient — only one record in memory at a time.
    Use this for pre-computation passes where you don't need
    all candidates loaded simultaneously.

    Yields:
        dict: Raw JSON dictionary for each candidate.
    """
    total = 100_000  # Known dataset size
    with open(filepath, "r", encoding="utf-8") as f:
        iterator = tqdm(f, total=total, desc="Loading candidates") if show_progress else f
        for line in iterator:
            line = line.strip()
            if line:
                yield orjson.loads(line)


def stream_candidates(
    filepath: Path = config.CANDIDATES_JSONL,
    show_progress: bool = True,
) -> Generator[Candidate, None, None]:
    """
    Stream parsed Candidate objects from candidates.jsonl.

    Memory efficient — only one Candidate in memory at a time.

    Yields:
        Candidate: Parsed candidate object.
    """
    for raw in stream_candidates_raw(filepath, show_progress):
        yield Candidate.from_dict(raw)


def load_all_candidates(
    filepath: Path = config.CANDIDATES_JSONL,
    show_progress: bool = True,
) -> list[Candidate]:
    """
    Load ALL candidates into memory.

    Warning: This uses ~2-4GB RAM for 100K candidates.
    Use only when you need random access to all candidates.
    For streaming operations, prefer stream_candidates().

    Returns:
        list[Candidate]: All 100K candidates.
    """
    return list(stream_candidates(filepath, show_progress))


def load_candidates_by_ids(
    candidate_ids: set[str],
    filepath: Path = config.CANDIDATES_JSONL,
    show_progress: bool = True,
) -> dict[str, Candidate]:
    """
    Load specific candidates by their IDs.

    Useful for loading only the top-K candidates after retrieval,
    without loading the entire dataset.

    Args:
        candidate_ids: Set of candidate IDs to load (e.g., {"CAND_0000001", "CAND_0000042"}).
        filepath: Path to candidates.jsonl.

    Returns:
        dict mapping candidate_id → Candidate for found candidates.
    """
    result: dict[str, Candidate] = {}
    remaining = set(candidate_ids)

    for raw in stream_candidates_raw(filepath, show_progress):
        cid = raw["candidate_id"]
        if cid in remaining:
            result[cid] = Candidate.from_dict(raw)
            remaining.discard(cid)
            if not remaining:
                break  # Found all requested candidates

    return result


def load_candidate_id_index(
    filepath: Path = config.CANDIDATES_JSONL,
    show_progress: bool = True,
) -> dict[str, int]:
    """
    Build an index mapping candidate_id → line number.

    Useful for quick lookup without loading all data.

    Returns:
        dict mapping candidate_id → line index (0-based).
    """
    index: dict[str, int] = {}
    for i, raw in enumerate(stream_candidates_raw(filepath, show_progress)):
        index[raw["candidate_id"]] = i
    return index


def get_candidate_text_for_embedding(candidate: Candidate) -> str:
    """
    Create a text representation of a candidate suitable for embedding.

    Combines headline, summary, skills, career descriptions, and
    education into a single text string for semantic embedding.
    Structured to capture the candidate's professional essence.

    Args:
        candidate: Parsed Candidate object.

    Returns:
        str: Concatenated text representation.
    """
    parts: list[str] = []

    # Headline and summary — most important context
    parts.append(candidate.profile.headline)
    parts.append(candidate.profile.summary)

    # Current role context
    parts.append(
        f"{candidate.profile.current_title} at {candidate.profile.current_company} "
        f"({candidate.profile.current_industry})"
    )

    # Skills with proficiency
    skill_parts = []
    for s in candidate.skills:
        if s.proficiency in ("advanced", "expert"):
            skill_parts.append(f"{s.name} ({s.proficiency})")
        else:
            skill_parts.append(s.name)
    if skill_parts:
        parts.append("Skills: " + ", ".join(skill_parts))

    # Career history — role descriptions contain rich signal
    for entry in candidate.career_history:
        parts.append(
            f"{entry.title} at {entry.company}: {entry.description}"
        )

    # Education
    for edu in candidate.education:
        parts.append(
            f"{edu.degree} in {edu.field_of_study} from {edu.institution}"
        )

    # Certifications
    for cert in candidate.certifications:
        parts.append(f"Certified: {cert.name} by {cert.issuer}")

    return " . ".join(parts)


def get_candidate_text_for_bm25(candidate: Candidate) -> str:
    """
    Create a text representation optimized for BM25 keyword matching.

    Differs from embedding text by repeating important terms and
    including more structured data for keyword overlap.

    Args:
        candidate: Parsed Candidate object.

    Returns:
        str: Text optimized for BM25 search.
    """
    parts: list[str] = []

    # Title and company (repeat for emphasis)
    parts.append(candidate.profile.current_title)
    parts.append(candidate.profile.headline)
    parts.append(candidate.profile.summary)

    # Skills — key for keyword matching
    for s in candidate.skills:
        parts.append(s.name)
        # Repeat high-proficiency skills for BM25 boost
        if s.proficiency in ("advanced", "expert"):
            parts.append(s.name)

    # Career descriptions
    for entry in candidate.career_history:
        parts.append(f"{entry.title} {entry.description}")

    # Education fields
    for edu in candidate.education:
        parts.append(f"{edu.degree} {edu.field_of_study}")

    # Certifications
    for cert in candidate.certifications:
        parts.append(cert.name)

    return " ".join(parts)
