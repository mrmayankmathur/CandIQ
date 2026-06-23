"""
Pre-computation script for Intelligent Candidate Discovery.

Runs ALL offline artifact generation steps in sequence.
This script CAN use network access (for downloading the embedding model)
and has no time constraint — run it before the contest evaluation.

Generated artifacts (saved to ranker/artifacts/):
    - candidate_features.parquet  — Feature matrix for all 100K candidates
    - candidate_embeddings.npy    — Dense embeddings (100K × 384)
    - candidate.index             — FAISS index for semantic search
    - candidate_ids.json          — Ordered candidate ID list
    - bm25_corpus.pkl             — Pickled BM25Okapi index

Usage:
    python -m ranker.precompute_all [--candidates path/to/candidates.jsonl]
"""

from __future__ import annotations

import os

# OpenMP runtime guard — must run before faiss / torch are imported (via the
# precompute submodules). faiss-cpu and torch each bundle their own libomp and
# the duplicate runtime segfaults on macOS. See rank.py for the same guard.
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

import argparse
import pickle
import time
from pathlib import Path

from tqdm import tqdm

from ranker import config
from ranker.utils.data_loader import (
    stream_candidates,
    get_candidate_text_for_bm25,
)
from ranker.models.candidate import Candidate
from ranker.precompute.extract_features import extract_all_features
from ranker.precompute.build_embeddings import build_embeddings, build_faiss_index


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        prog="ranker.precompute_all",
        description="Pre-compute all artifacts for the ranking pipeline",
    )
    parser.add_argument(
        "--candidates",
        type=Path,
        default=config.CANDIDATES_JSONL,
        help="Path to candidates.jsonl (default: %(default)s)",
    )
    parser.add_argument(
        "--artifacts-dir",
        type=Path,
        default=config.ARTIFACTS_DIR,
        help="Output directory for artifacts (default: %(default)s)",
    )
    parser.add_argument(
        "--skip-features",
        action="store_true",
        help="Skip feature extraction (use existing parquet)",
    )
    parser.add_argument(
        "--skip-embeddings",
        action="store_true",
        help="Skip embedding generation (use existing npy + index)",
    )
    parser.add_argument(
        "--skip-bm25",
        action="store_true",
        help="Skip BM25 index building (use existing pickle)",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=config.EMBEDDING_BATCH_SIZE,
        help="Batch size for embedding generation (default: %(default)s)",
    )
    return parser.parse_args()


def step_1_extract_features(candidates_path: Path, artifacts_dir: Path) -> None:
    """
    Step 1: Extract features for all candidates.

    Streams through candidates.jsonl and computes structured features
    (skill match scores, experience fit, career quality, behavioral signals, etc.)
    for each candidate. Saves as a Polars-compatible Parquet file.
    """
    print("\n" + "=" * 60)
    print("  📊 Step 1: Feature Extraction")
    print("=" * 60)

    output_path = artifacts_dir / "candidate_features.parquet"
    start = time.perf_counter()

    extract_all_features(
        filepath=candidates_path,
        output_path=output_path,
    )

    elapsed = time.perf_counter() - start
    print(f"  ✅ Features saved to {output_path}")
    print(f"  ⏱  Elapsed: {elapsed:.1f}s")


def step_2_build_embeddings(
    candidates_path: Path,
    artifacts_dir: Path,
    batch_size: int,
) -> None:
    """
    Step 2: Generate embeddings and build FAISS index.

    Delegates to the existing build_embeddings() and build_faiss_index()
    functions in the precompute module.
    """
    print("\n" + "=" * 60)
    print("  🧠 Step 2: Embedding Generation + FAISS Index")
    print("=" * 60)

    start = time.perf_counter()

    # Step 2a: Compute embeddings and save
    embeddings_array, candidate_ids = build_embeddings(
        filepath=candidates_path,
        output_dir=artifacts_dir,
        batch_size=batch_size,
    )
    print(f"  ✓ {len(candidate_ids)} candidates embedded → {embeddings_array.shape}")

    # Step 2b: Build FAISS index from saved embeddings file
    embeddings_path = artifacts_dir / "candidate_embeddings.npy"
    index_path = artifacts_dir / "candidate.index"
    build_faiss_index(
        embeddings_path=embeddings_path,
        index_path=index_path,
    )
    print(f"  ✓ FAISS index built and saved")

    elapsed = time.perf_counter() - start
    print(f"  ⏱  Elapsed: {elapsed:.1f}s")


def step_3_build_bm25(candidates_path: Path, artifacts_dir: Path) -> None:
    """
    Step 3: Build BM25 keyword search index.

    Creates tokenized BM25 corpus from candidate text representations
    and pickles the BM25Okapi object for use during ranking.
    """
    print("\n" + "=" * 60)
    print("  🔍 Step 3: BM25 Index")
    print("=" * 60)

    start = time.perf_counter()

    from rank_bm25 import BM25Okapi

    # Tokenize candidate texts for BM25
    print("  Tokenizing candidate corpus...")
    tokenized_corpus: list[list[str]] = []

    for candidate in stream_candidates(candidates_path, show_progress=True):
        text = get_candidate_text_for_bm25(candidate)
        # Simple whitespace tokenization + lowercasing
        tokens = text.lower().split()
        tokenized_corpus.append(tokens)

    print(f"  ✓ {len(tokenized_corpus)} documents tokenized")

    # Build BM25 index
    print("  Building BM25Okapi index...")
    bm25 = BM25Okapi(tokenized_corpus)

    # Pickle the index
    bm25_path = artifacts_dir / "bm25_corpus.pkl"
    with open(bm25_path, "wb") as f:
        pickle.dump(bm25, f, protocol=pickle.HIGHEST_PROTOCOL)

    file_size_mb = bm25_path.stat().st_size / (1024 * 1024)
    print(f"  ✓ BM25 index saved to {bm25_path} ({file_size_mb:.1f} MB)")

    elapsed = time.perf_counter() - start
    print(f"  ⏱  Elapsed: {elapsed:.1f}s")


def main() -> None:
    """Run all pre-computation steps in sequence."""
    total_start = time.perf_counter()

    print("=" * 60)
    print("  🏗️  Intelligent Candidate Discovery — Pre-computation")
    print("=" * 60)

    args = parse_args()
    print(f"\n  Candidates: {args.candidates}")
    print(f"  Artifacts:  {args.artifacts_dir}")

    # Ensure artifacts directory exists
    args.artifacts_dir.mkdir(parents=True, exist_ok=True)

    # Validate input file
    if not args.candidates.exists():
        print(f"\n  ❌ Error: Candidates file not found: {args.candidates}")
        print("  Please ensure the dataset is available.")
        return

    # Step 1: Feature Extraction
    if not args.skip_features:
        step_1_extract_features(args.candidates, args.artifacts_dir)
    else:
        print("\n  ⏭️  Skipping feature extraction (--skip-features)")

    # Step 2: Embeddings + FAISS
    if not args.skip_embeddings:
        step_2_build_embeddings(args.candidates, args.artifacts_dir, args.batch_size)
    else:
        print("\n  ⏭️  Skipping embeddings (--skip-embeddings)")

    # Step 3: BM25 Index
    if not args.skip_bm25:
        step_3_build_bm25(args.candidates, args.artifacts_dir)
    else:
        print("\n  ⏭️  Skipping BM25 index (--skip-bm25)")

    # Summary
    total_elapsed = time.perf_counter() - total_start
    print(f"\n{'=' * 60}")
    print(f"  ✅ All pre-computation steps complete!")
    print(f"  ⏱  Total elapsed: {total_elapsed:.1f}s ({total_elapsed / 60:.1f} min)")
    print(f"{'=' * 60}")

    # List generated artifacts
    print(f"\n  Generated artifacts in {args.artifacts_dir}:")
    for artifact in sorted(args.artifacts_dir.iterdir()):
        if artifact.is_file() and not artifact.name.startswith("."):
            size_mb = artifact.stat().st_size / (1024 * 1024)
            print(f"    • {artifact.name:40s} {size_mb:8.1f} MB")


if __name__ == "__main__":
    main()
