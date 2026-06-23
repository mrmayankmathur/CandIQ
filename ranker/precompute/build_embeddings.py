"""
Embedding pre-computation and FAISS index builder.

Computes dense embeddings for all 100K candidates using
BAAI/bge-small-en-v1.5 (384-dim), then builds a FAISS inner-product
index for fast cosine-similarity retrieval.

Outputs:
  - candidate_embeddings.npy  — (N, 384) float32 array
  - candidate_ids.json        — ordered list of candidate IDs
  - candidate.index           — FAISS IndexFlatIP
"""

from __future__ import annotations

import json
import os
import time
from pathlib import Path
from typing import Optional

import numpy as np
import torch
from sentence_transformers import SentenceTransformer
from tqdm import tqdm

from ranker import config
from ranker.utils.data_loader import stream_candidates, get_candidate_text_for_embedding

# Use all available CPU cores for embedding throughput. torch defaults to half
# the cores; embedding 100K candidates is the precompute bottleneck so we want
# every core. (KMP_DUPLICATE_LIB_OK in ranker/__init__ keeps this crash-free.)
torch.set_num_threads(os.cpu_count() or 4)

# BGE models require this prefix for optimal performance
_BGE_PREFIX = "Represent this sentence: "


def build_embeddings(
    filepath: Path = config.CANDIDATES_JSONL,
    output_dir: Path = config.ARTIFACTS_DIR,
    batch_size: int = config.EMBEDDING_BATCH_SIZE,
) -> tuple[np.ndarray, list[str]]:
    """
    Stream all candidates, compute embeddings with BGE-small-en-v1.5, and save.

    Process candidates in batches for memory efficiency and GPU utilization.
    Prepends the BGE instruction prefix to each candidate text.

    Args:
        filepath: Path to candidates.jsonl.
        output_dir: Directory to save embeddings and candidate IDs.
        batch_size: Number of candidates to embed per batch.

    Returns:
        Tuple of (embeddings array [N, 384], candidate_ids list).
    """
    print(f"Loading model: {config.EMBEDDING_MODEL_NAME}")
    # Force CPU: the contest reproduces on CPU-only, and auto-device selection
    # picks Apple MPS which OOMs at 100K scale. CPU keeps precompute aligned
    # with the ranking environment and avoids the MPS memory ceiling.
    model = SentenceTransformer(config.EMBEDDING_MODEL_NAME, device="cpu")
    # Cap sequence length: candidate texts are front-loaded (headline, summary,
    # skills, then career), so the first ~160 tokens carry the strongest signal.
    # Default 512 is ~4x slower on CPU for marginal quality gain at this scale.
    model.max_seq_length = config.EMBEDDING_MAX_SEQ_LENGTH
    print(f"  Embedding dimension: {config.EMBEDDING_DIM}")
    print(f"  Max sequence length: {model.max_seq_length}")
    print(f"  Batch size: {batch_size}")
    print()

    candidate_ids: list[str] = []
    all_embeddings: list[np.ndarray] = []

    # Accumulate a batch of texts, then encode
    batch_texts: list[str] = []
    batch_ids: list[str] = []

    total_processed = 0
    start_time = time.time()

    for candidate in tqdm(
        stream_candidates(filepath, show_progress=False),
        total=100_000,
        desc="Computing embeddings",
    ):
        # Get text and prepend BGE prefix
        text = get_candidate_text_for_embedding(candidate)
        batch_texts.append(f"{_BGE_PREFIX}{text}")
        batch_ids.append(candidate.candidate_id)

        if len(batch_texts) >= batch_size:
            # Encode batch
            embeddings = model.encode(
                batch_texts,
                batch_size=batch_size,
                show_progress_bar=False,
                normalize_embeddings=False,  # We normalize later for FAISS
            )
            all_embeddings.append(embeddings)
            candidate_ids.extend(batch_ids)

            total_processed += len(batch_texts)
            batch_texts.clear()
            batch_ids.clear()

    # Handle remaining candidates in last partial batch
    if batch_texts:
        embeddings = model.encode(
            batch_texts,
            batch_size=batch_size,
            show_progress_bar=False,
            normalize_embeddings=False,
        )
        all_embeddings.append(embeddings)
        candidate_ids.extend(batch_ids)
        total_processed += len(batch_texts)

    elapsed = time.time() - start_time
    print(f"\n✅ Encoded {total_processed} candidates in {elapsed:.1f}s")
    print(f"   ({total_processed / elapsed:.0f} candidates/sec)")

    # Stack all batches into single array
    embeddings_array = np.vstack(all_embeddings).astype(np.float32)
    print(f"   Embeddings shape: {embeddings_array.shape}")

    # Save outputs
    output_dir.mkdir(parents=True, exist_ok=True)

    embeddings_path = output_dir / config.EMBEDDINGS_FILE.name
    np.save(embeddings_path, embeddings_array)
    print(f"   Saved embeddings → {embeddings_path}")

    ids_path = output_dir / config.CANDIDATE_IDS_FILE.name
    with open(ids_path, "w") as f:
        json.dump(candidate_ids, f)
    print(f"   Saved candidate IDs → {ids_path}")

    return embeddings_array, candidate_ids


def build_faiss_index(
    embeddings_path: Path = config.EMBEDDINGS_FILE,
    index_path: Path = config.FAISS_INDEX_FILE,
) -> faiss.IndexFlatIP:
    """
    Build a FAISS IndexFlatIP from pre-computed embeddings.

    Normalizes vectors to unit length so that inner product = cosine similarity.

    Args:
        embeddings_path: Path to the .npy embeddings file.
        index_path: Destination for the FAISS index file.

    Returns:
        faiss.IndexFlatIP: The built FAISS index.
    """
    # Lazy import: faiss bundles its own libomp, which segfaults if loaded
    # before the multithreaded torch encode in build_embeddings(). Importing it
    # here (after encoding is done) keeps both phases crash-free.
    import faiss

    print(f"Loading embeddings from {embeddings_path}")
    embeddings = np.load(embeddings_path).astype(np.float32)
    print(f"  Shape: {embeddings.shape}")

    # L2-normalize so inner product ≡ cosine similarity
    print("  Normalizing vectors...")
    faiss.normalize_L2(embeddings)

    # Build flat inner-product index (exact search, no quantization)
    dim = embeddings.shape[1]
    index = faiss.IndexFlatIP(dim)
    index.add(embeddings)

    print(f"  Index size: {index.ntotal} vectors, dim={dim}")

    # Save index
    index_path.parent.mkdir(parents=True, exist_ok=True)
    faiss.write_index(index, str(index_path))
    print(f"  ✅ Saved FAISS index → {index_path}")

    return index


def get_query_embedding(
    text: str,
    model: Optional[SentenceTransformer] = None,
) -> np.ndarray:
    """
    Embed a query text using the same BGE model.

    For BGE models, the query prefix differs from the document prefix,
    but for bge-small-en-v1.5 the recommended approach is to use
    'Represent this sentence: ' for both queries and documents.

    Args:
        text: The query text to embed.
        model: Pre-loaded SentenceTransformer model. If None, loads fresh.

    Returns:
        np.ndarray: Normalized query embedding of shape (384,).
    """
    if model is None:
        model = SentenceTransformer(config.EMBEDDING_MODEL_NAME)

    query_text = f"{_BGE_PREFIX}{text}"
    embedding = model.encode(
        [query_text],
        normalize_embeddings=True,  # Normalize for cosine similarity
        show_progress_bar=False,
    )
    return embedding[0].astype(np.float32)


def main() -> None:
    """
    Full embedding pipeline: compute embeddings → build FAISS index.
    """
    print("=" * 70)
    print("  Intelligent Candidate Discovery — Embedding Pipeline")
    print("=" * 70)
    print(f"  Model:  {config.EMBEDDING_MODEL_NAME}")
    print(f"  Input:  {config.CANDIDATES_JSONL}")
    print(f"  Output: {config.ARTIFACTS_DIR}")
    print()

    # Step 1: Compute embeddings
    print("─" * 70)
    print("  Step 1: Computing candidate embeddings")
    print("─" * 70)
    embeddings, candidate_ids = build_embeddings()

    # Step 2: Build FAISS index
    print()
    print("─" * 70)
    print("  Step 2: Building FAISS index")
    print("─" * 70)
    index = build_faiss_index()

    print()
    print("=" * 70)
    print(f"  ✅ Pipeline complete!")
    print(f"     {len(candidate_ids)} candidates embedded")
    print(f"     FAISS index: {index.ntotal} vectors")
    print("=" * 70)


# ═══════════════════════════════════════════════════════════════════════════════
# CLI Entry Point
# ═══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    main()
