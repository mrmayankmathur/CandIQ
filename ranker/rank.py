"""
Main orchestrator for the CandIQ.ai ranking pipeline.

Runs the full 4-stage ranking pipeline within contest constraints:
  - 5-minute wall-clock limit
  - 16 GB RAM (CPU-only, no GPU)
  - No network access

Usage:
    python -m ranker.rank \
        --candidates dataset/candidates.jsonl \
        --out submission.csv \
        --artifacts-dir ranker/artifacts

Stages:
    1. Hybrid Retrieval  — FAISS semantic + BM25 keyword → RRF fusion → top 500
    2. Filtering          — Honeypot detection + disqualifier checks
    3. Scoring            — Feature-weighted final score
    4. Output             — Top 100 with reasoning → CSV
"""

from __future__ import annotations

import os

# ── OpenMP runtime guard (MUST run before importing faiss / torch) ─>
# On macOS, faiss-cpu and torch each bundle their own libomp. Loading the
# FAISS index and then calling torch's encode() in the same process triggers a
# duplicate-OpenMP abort (segfault, exit 139). Allowing the duplicate runtime
# and pinning thread counts keeps the offline ranking step deterministic and
# crash-free on CPU.
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")

import argparse
import json
import pickle
import sys
import time
from pathlib import Path

import faiss
import numpy as np
import polars as pl
from sentence_transformers import SentenceTransformer

from ranker import config
from ranker.models.candidate import Candidate
from ranker.utils.data_loader import load_candidates_by_ids
from ranker.pipeline.retrieval import (
    semantic_search,
    bm25_search,
    rrf_fusion,
    hybrid_search,
)
from ranker.pipeline.filtering import (
    detect_honeypot,
    check_disqualifiers,
    filter_candidates,
)
from ranker.pipeline.scoring import compute_final_score, rank_candidates
from ranker.pipeline.reasoning import generate_reasoning, generate_all_reasonings


# ─── Helpers ────────────────────────────────────────────────────────────────────


class Timer:
    """Context manager for timing pipeline stages."""

    def __init__(self, label: str):
        self.label = label
        self.elapsed: float = 0.0

    def __enter__(self):
        self.start = time.perf_counter()
        print(f"\n{'─' * 60}")
        print(f"⏱  Stage: {self.label}")
        print(f"{'─' * 60}")
        return self

    def __exit__(self, *args):
        self.elapsed = time.perf_counter() - self.start
        print(f"✅  {self.label} completed in {self.elapsed:.2f}s")


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        prog="ranker.rank",
        description="CandIQ.ai — Ranking Pipeline",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--candidates",
        type=Path,
        default=config.CANDIDATES_JSONL,
        help="Path to candidates.jsonl (default: %(default)s)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=config.SUBMISSION_CSV,
        help="Output CSV path (default: %(default)s)",
    )
    parser.add_argument(
        "--artifacts-dir",
        type=Path,
        default=config.ARTIFACTS_DIR,
        help="Directory with pre-computed artifacts (default: %(default)s)",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=config.TOP_K_OUTPUT,
        help="Number of top candidates to output (default: %(default)s)",
    )
    parser.add_argument(
        "--retrieval-k",
        type=int,
        default=config.RRF_TOP_K,
        help="Number of candidates to retrieve in Stage 1 (default: %(default)s)",
    )
    parser.add_argument(
        "--skip-reasoning",
        action="store_true",
        help="Skip reasoning generation (faster, for debugging)",
    )
    return parser.parse_args()


# ─── Artifact Loading ───────────────────────────────────────────────────────────


def load_artifacts(artifacts_dir: Path) -> dict:
    """
    Load all pre-computed artifacts from disk.

    Returns a dict with keys:
        faiss_index, candidate_ids, features_df, bm25_index, jd_intent
    """
    artifacts = {}

    # 1. FAISS index
    faiss_path = artifacts_dir / "candidate.index"
    if not faiss_path.exists():
        raise FileNotFoundError(f"FAISS index not found: {faiss_path}")
    artifacts["faiss_index"] = faiss.read_index(str(faiss_path))
    print(f"  ✓ FAISS index loaded ({artifacts['faiss_index'].ntotal} vectors)")

    # 2. Candidate IDs (ordered list matching FAISS index rows)
    ids_path = artifacts_dir / "candidate_ids.json"
    if not ids_path.exists():
        raise FileNotFoundError(f"Candidate IDs not found: {ids_path}")
    with open(ids_path, "r") as f:
        artifacts["candidate_ids"] = json.load(f)
    print(f"  ✓ Candidate IDs loaded ({len(artifacts['candidate_ids'])} IDs)")

    # 3. Feature matrix (Polars DataFrame)
    features_path = artifacts_dir / "candidate_features.parquet"
    if not features_path.exists():
        raise FileNotFoundError(f"Feature matrix not found: {features_path}")
    artifacts["features_df"] = pl.read_parquet(features_path)
    print(f"  ✓ Feature matrix loaded {artifacts['features_df'].shape}")

    # 4. BM25 index (pickled BM25Okapi)
    bm25_path = artifacts_dir / "bm25_corpus.pkl"
    if not bm25_path.exists():
        raise FileNotFoundError(f"BM25 corpus not found: {bm25_path}")
    with open(bm25_path, "rb") as f:
        artifacts["bm25_index"] = pickle.load(f)
    print(f"  ✓ BM25 index loaded")

    # 5. JD intent
    jd_path = artifacts_dir / "jd_intent.json"
    if not jd_path.exists():
        raise FileNotFoundError(f"JD intent not found: {jd_path}")
    with open(jd_path, "r") as f:
        artifacts["jd_intent"] = json.load(f)
    print(f"  ✓ JD intent loaded: {artifacts['jd_intent'].get('job_title', 'N/A')}")

    return artifacts


def build_query_text(jd_intent: dict) -> str:
    """
    Build a rich query string from the parsed JD intent.

    Combines must-have skills, nice-to-have skills, expanded search terms,
    and the job summary to create a comprehensive query for retrieval.
    """
    parts: list[str] = []

    # Job summary — captures the high-level intent
    if jd_intent.get("summary"):
        parts.append(jd_intent["summary"])

    # Must-have skill keywords (highest signal)
    for skill_group in jd_intent.get("must_have_skills", []):
        parts.extend(skill_group.get("keywords", []))
        if skill_group.get("description"):
            parts.append(skill_group["description"])

    # Nice-to-have skill keywords
    for skill_group in jd_intent.get("nice_to_have_skills", []):
        parts.extend(skill_group.get("keywords", []))

    # Expanded search terms (semantic coverage)
    expanded = jd_intent.get("expanded_search_terms", {})
    for category_terms in expanded.values():
        if isinstance(category_terms, list):
            parts.extend(category_terms)

    return " . ".join(parts)


# ─── Pipeline Stages ────────────────────────────────────────────────────────────


def stage_1_retrieval(
    query_text: str,
    faiss_index,
    candidate_ids: list[str],
    bm25_index,
    model: SentenceTransformer,
    top_k: int = 500,
) -> list[tuple[str, float]]:
    """
    Stage 1: Hybrid Retrieval.

    Combines semantic search (FAISS) and keyword search (BM25) via
    Reciprocal Rank Fusion (RRF) to retrieve a diverse candidate pool.

    Returns:
        List of (candidate_id, rrf_score) tuples, sorted by score descending.
    """
    # Semantic search via FAISS
    sem_results = semantic_search(
        query_text=query_text,
        index=faiss_index,
        candidate_ids=candidate_ids,
        model=model,
        top_k=top_k,
    )
    print(f"  → Semantic search: {len(sem_results)} candidates")

    # BM25 keyword search
    bm25_results = bm25_search(
        query_text=query_text,
        bm25=bm25_index,
        candidate_ids=candidate_ids,
        top_k=top_k,
    )
    print(f"  → BM25 search: {len(bm25_results)} candidates")

    # Reciprocal Rank Fusion
    fused = rrf_fusion(
        semantic_results=sem_results,
        bm25_results=bm25_results,
        k=config.RRF_K,
    )
    print(f"  → RRF fusion: {len(fused)} candidates")

    return fused[:top_k]


def stage_2_filtering(
    candidates: dict[str, Candidate],
    retrieval_scores: dict[str, float],
) -> tuple[dict[str, Candidate], dict[str, float]]:
    """
    Stage 2: Filtering.

    Removes honeypot candidates (fake profiles planted in the dataset)
    and candidates who hit hard/soft disqualifiers from the JD.

    Returns:
        Tuple of (filtered_candidates, filtered_scores).
    """
    candidate_ids = list(candidates.keys())
    filtered_ids, rejection_log = filter_candidates(candidate_ids, candidates)

    # Log rejections
    if rejection_log:
        print(f"  → Rejection details:")
        for cid, reasons in list(rejection_log.items())[:5]:
            print(f"     {cid}: {reasons[0]}")
        if len(rejection_log) > 5:
            print(f"     ... and {len(rejection_log) - 5} more")

    # Build filtered maps
    filtered_id_set = set(filtered_ids)
    filtered_candidates = {cid: c for cid, c in candidates.items() if cid in filtered_id_set}
    filtered_scores = {cid: s for cid, s in retrieval_scores.items() if cid in filtered_id_set}

    removed = len(candidates) - len(filtered_candidates)
    print(f"  → Removed {removed} candidates (honeypots + disqualified)")
    print(f"  → {len(filtered_candidates)} candidates remaining")

    return filtered_candidates, filtered_scores


def stage_3_scoring(
    candidates: dict[str, Candidate],
    features_df: pl.DataFrame,
    retrieval_scores: dict[str, float],
) -> list[tuple[str, float]]:
    """
    Stage 3: Scoring.

    Computes a weighted final score for each remaining candidate using
    pre-extracted features and retrieval scores.

    Returns:
        List of (candidate_id, final_score) sorted by score descending.
    """
    # Filter features to only remaining candidates
    remaining_ids = set(candidates.keys())
    filtered_df = features_df.filter(
        pl.col("candidate_id").is_in(remaining_ids)
    )
    print(f"  → Features loaded for {len(filtered_df)} candidates")

    # Convert Polars DataFrame to dict[str, dict] for scoring module
    candidate_features: dict[str, dict] = {}
    columns = filtered_df.columns
    for row in filtered_df.iter_rows(named=True):
        cid = row["candidate_id"]
        candidate_features[cid] = {k: v for k, v in row.items() if k != "candidate_id"}

    # Rank using feature-weighted scoring
    ranked = rank_candidates(
        candidate_features=candidate_features,
        retrieval_scores=retrieval_scores,
    )
    print(f"  → Ranked {len(ranked)} candidates")

    return ranked


def stage_4_output(
    ranked: list[tuple[str, float]],
    candidates: dict[str, Candidate],
    features_df: pl.DataFrame,
    top_k: int,
    out_path: Path,
    skip_reasoning: bool = False,
) -> None:
    """
    Stage 4: Output.

    Takes the top-K ranked candidates, generates natural-language
    reasoning for each, and writes the final submission CSV.
    """
    # Take top K
    top_ranked = ranked[:top_k]
    print(f"  → Selected top {len(top_ranked)} candidates")

    # Ensure scores are non-increasing (monotonicity guarantee)
    validated: list[tuple[str, float]] = []
    max_score_so_far = float("inf")
    for cid, score in top_ranked:
        clamped = min(score, max_score_so_far)
        validated.append((cid, clamped))
        max_score_so_far = clamped

    # Generate reasonings
    if not skip_reasoning:
        # Build features dict from Polars DataFrame for reasoning module
        top_cids = {cid for cid, _ in validated}
        top_df = features_df.filter(pl.col("candidate_id").is_in(top_cids))
        features_dict: dict[str, dict] = {}
        for row in top_df.iter_rows(named=True):
            cid = row["candidate_id"]
            features_dict[cid] = {k: v for k, v in row.items() if k != "candidate_id"}

        reasonings = generate_all_reasonings(
            ranked_candidates=validated,
            candidates=candidates,
            features=features_dict,
        )
        reasoning_map = {cid: reasoning for cid, _rank, _score, reasoning in reasonings}
    else:
        reasoning_map = {}
        print("  \u2192 Reasoning generation skipped (--skip-reasoning)")

    # Write CSV
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("candidate_id,rank,score,reasoning\n")
        for rank_num, (cid, score) in enumerate(validated, start=1):
            reasoning = reasoning_map.get(cid, "")
            # Escape reasoning for CSV (double-quote any internal quotes)
            reasoning_escaped = reasoning.replace('"', '""')
            f.write(f'{cid},{rank_num},{score:.6f},"{reasoning_escaped}"\n')

    print(f"  → CSV written to {out_path}")
    print(f"  → {len(validated)} candidates in output")

    # Summary stats
    scores = [s for _, s in validated]
    if scores:
        print(f"\n  📊 Score Distribution:")
        print(f"     Max:    {max(scores):.6f}")
        print(f"     Min:    {min(scores):.6f}")
        print(f"     Mean:   {sum(scores) / len(scores):.6f}")
        print(f"     Median: {sorted(scores)[len(scores) // 2]:.6f}")


# ─── Main ───────────────────────────────────────────────────────────────────────


def main() -> None:
    """Run the complete ranking pipeline."""
    pipeline_start = time.perf_counter()

    print("=" * 60)
    print("  🚀 CandIQ.ai — Ranking Pipeline")
    print("=" * 60)

    args = parse_args()
    print(f"\n  Candidates: {args.candidates}")
    print(f"  Output:     {args.out}")
    print(f"  Artifacts:  {args.artifacts_dir}")
    print(f"  Top-K:      {args.top_k}")

    # ── Load Artifacts ────────────────────────────────────────────────────────

    with Timer("Loading pre-computed artifacts") as t_load:
        artifacts = load_artifacts(args.artifacts_dir)

    # ── Build Query ───────────────────────────────────────────────────────────

    query_text = build_query_text(artifacts["jd_intent"])
    print(f"\n  Query text length: {len(query_text)} chars")

    # ── Load Embedding Model ──────────────────────────────────────────────────

    with Timer("Loading embedding model") as t_model:
        model = SentenceTransformer(
            config.EMBEDDING_MODEL_NAME,
            device="cpu",
        )
        print(f"  ✓ Model loaded: {config.EMBEDDING_MODEL_NAME}")

    # ── Stage 1: Hybrid Retrieval ─────────────────────────────────────────────

    with Timer("Stage 1 — Hybrid Retrieval") as t_retrieval:
        retrieval_results = stage_1_retrieval(
            query_text=query_text,
            faiss_index=artifacts["faiss_index"],
            candidate_ids=artifacts["candidate_ids"],
            bm25_index=artifacts["bm25_index"],
            model=model,
            top_k=args.retrieval_k,
        )

    # ── Load Candidate Data for Retrieved Set ─────────────────────────────────

    with Timer("Loading candidate data for retrieved set") as t_data:
        retrieved_ids = {cid for cid, _ in retrieval_results}
        candidates = load_candidates_by_ids(
            candidate_ids=retrieved_ids,
            filepath=args.candidates,
            show_progress=True,
        )
        retrieval_scores = {cid: score for cid, score in retrieval_results}
        print(f"  ✓ Loaded {len(candidates)} candidate profiles")

    # ── Stage 2: Filtering ────────────────────────────────────────────────────

    with Timer("Stage 2 — Filtering") as t_filter:
        candidates, retrieval_scores = stage_2_filtering(
            candidates=candidates,
            retrieval_scores=retrieval_scores,
        )

    # ── Stage 3: Scoring ──────────────────────────────────────────────────────

    with Timer("Stage 3 — Scoring") as t_score:
        ranked = stage_3_scoring(
            candidates=candidates,
            features_df=artifacts["features_df"],
            retrieval_scores=retrieval_scores,
        )

    # ── Stage 4: Output ───────────────────────────────────────────────────────

    with Timer("Stage 4 — Output") as t_output:
        stage_4_output(
            ranked=ranked,
            candidates=candidates,
            features_df=artifacts["features_df"],
            top_k=args.top_k,
            out_path=args.out,
            skip_reasoning=args.skip_reasoning,
        )

    # ── Summary ───────────────────────────────────────────────────────────────

    total_elapsed = time.perf_counter() - pipeline_start
    print(f"\n{'=' * 60}")
    print(f"  ⏱  Pipeline Timing Summary")
    print(f"{'=' * 60}")
    print(f"  Artifact loading:     {t_load.elapsed:6.2f}s")
    print(f"  Model loading:        {t_model.elapsed:6.2f}s")
    print(f"  Stage 1 (Retrieval):  {t_retrieval.elapsed:6.2f}s")
    print(f"  Stage 2 (Data load):  {t_data.elapsed:6.2f}s")
    print(f"  Stage 3 (Filtering):  {t_filter.elapsed:6.2f}s")
    print(f"  Stage 4 (Scoring):    {t_score.elapsed:6.2f}s")
    print(f"  Stage 5 (Output):     {t_output.elapsed:6.2f}s")
    print(f"  {'─' * 30}")
    print(f"  TOTAL:                {total_elapsed:6.2f}s")
    print(f"{'=' * 60}")

    if total_elapsed > 300:
        print("  ⚠️  WARNING: Pipeline exceeded 5-minute limit!")
    else:
        remaining = 300 - total_elapsed
        print(f"  ✅  Within time limit ({remaining:.0f}s remaining)")


if __name__ == "__main__":
    main()
