"""
Hybrid retrieval pipeline: FAISS semantic search + BM25 keyword search + RRF fusion.

Retrieves candidate shortlist from 100K candidates using:
1. FAISS ANN for semantic similarity (dense retrieval)
2. BM25Okapi for keyword overlap (sparse retrieval)
3. Reciprocal Rank Fusion to merge both ranked lists
"""

from __future__ import annotations

import json
import re
from typing import Optional

import faiss
import numpy as np
from rank_bm25 import BM25Okapi
from sentence_transformers import SentenceTransformer

from ranker import config


def load_faiss_index(index_path: str | None = None) -> faiss.Index:
    """
    Load a FAISS index from disk.

    Args:
        index_path: Path to the .index file. Defaults to config.FAISS_INDEX_FILE.

    Returns:
        faiss.Index: The loaded FAISS index.
    """
    if index_path is None:
        index_path = str(config.FAISS_INDEX_FILE)
    return faiss.read_index(str(index_path))


def load_candidate_ids(ids_path: str | None = None) -> list[str]:
    """
    Load the ordered list of candidate IDs that correspond to FAISS index rows.

    Args:
        ids_path: Path to the JSON file. Defaults to config.CANDIDATE_IDS_FILE.

    Returns:
        list[str]: Ordered list of candidate IDs (index-aligned with FAISS).
    """
    if ids_path is None:
        ids_path = str(config.CANDIDATE_IDS_FILE)
    with open(ids_path, "r", encoding="utf-8") as f:
        return json.load(f)


def semantic_search(
    query_text: str,
    index: faiss.Index,
    candidate_ids: list[str],
    model: SentenceTransformer,
    top_k: int = 500,
) -> list[tuple[str, float]]:
    """
    Embed query with sentence-transformers, search FAISS index.

    For BGE models, automatically prepends the recommended retrieval prefix
    to improve query embedding quality.

    Args:
        query_text: The JD or search query text.
        index: Pre-built FAISS index.
        candidate_ids: Ordered candidate IDs aligned with index rows.
        model: Loaded SentenceTransformer model.
        top_k: Number of nearest neighbors to retrieve.

    Returns:
        list[tuple[str, float]]: (candidate_id, similarity_score) sorted by score desc.
    """
    # BGE models need a query prefix for retrieval tasks
    model_name = getattr(model, "model_card_data", None)
    if model_name is None:
        model_name_str = ""
    else:
        model_name_str = str(model_name)

    # Check if this is a BGE model by looking at the model's name/path
    is_bge = "bge" in config.EMBEDDING_MODEL_NAME.lower()
    if is_bge:
        query_text = (
            "Represent this sentence for retrieving relevant passages: "
            + query_text
        )

    # Encode and normalize query vector
    query_vec = model.encode([query_text], normalize_embeddings=True)
    query_vec = np.array(query_vec, dtype=np.float32)

    # Search FAISS index
    distances, indices = index.search(query_vec, top_k)

    results: list[tuple[str, float]] = []
    for dist, idx in zip(distances[0], indices[0]):
        if idx == -1:
            # FAISS returns -1 for empty slots
            continue
        if idx < len(candidate_ids):
            results.append((candidate_ids[idx], float(dist)))

    return results


def _tokenize(text: str) -> list[str]:
    """
    Tokenize text for BM25: lowercase + split on non-alphanumeric characters.

    Args:
        text: Input text to tokenize.

    Returns:
        list[str]: List of lowercase tokens.
    """
    return re.findall(r"[a-z0-9]+", text.lower())


def build_bm25_index(corpus_texts: list[str]) -> BM25Okapi:
    """
    Build a BM25Okapi index from candidate text representations.

    Tokenizes each document by lowercasing and splitting on non-alphanumeric
    characters to ensure consistent matching.

    Args:
        corpus_texts: List of candidate text representations
                      (from get_candidate_text_for_bm25).

    Returns:
        BM25Okapi: Ready-to-query BM25 index.
    """
    tokenized_corpus = [_tokenize(text) for text in corpus_texts]
    return BM25Okapi(tokenized_corpus)


def bm25_search(
    query_text: str,
    bm25: BM25Okapi,
    candidate_ids: list[str],
    top_k: int = 500,
    max_query_tokens: int = 60,
) -> list[tuple[str, float]]:
    """
    Search the BM25 index with a query and return top-K results.

    Uses the same tokenization as build_bm25_index for consistency.
    Limits query tokens to reduce scoring time on 100K docs.

    Args:
        query_text: The JD or search query text.
        bm25: Pre-built BM25Okapi index.
        candidate_ids: Ordered candidate IDs aligned with BM25 corpus rows.
        top_k: Number of top results to return.
        max_query_tokens: Max query tokens (longer queries are very slow).

    Returns:
        list[tuple[str, float]]: (candidate_id, bm25_score) sorted by score desc.
    """
    query_tokens = _tokenize(query_text)

    # Deduplicate and limit tokens for performance — BM25 scoring is
    # O(query_tokens × corpus_size), so fewer tokens = much faster
    seen = set()
    unique_tokens = []
    for t in query_tokens:
        if t not in seen and len(t) > 1:  # skip single-char tokens
            seen.add(t)
            unique_tokens.append(t)
    query_tokens = unique_tokens[:max_query_tokens]

    scores = bm25.get_scores(query_tokens)

    # Use argpartition for O(n) partial sort instead of O(n log n) full sort
    if len(scores) > top_k:
        partition_idx = np.argpartition(scores, -top_k)[-top_k:]
        # Now sort only the top_k for correct ordering
        sorted_within = partition_idx[np.argsort(scores[partition_idx])[::-1]]
    else:
        sorted_within = np.argsort(scores)[::-1]

    results: list[tuple[str, float]] = []
    for idx in sorted_within:
        if scores[idx] > 0:  # Skip zero-score candidates
            results.append((candidate_ids[idx], float(scores[idx])))

    return results


def rrf_fusion(
    semantic_results: list[tuple[str, float]],
    bm25_results: list[tuple[str, float]],
    k: int = 60,
) -> list[tuple[str, float]]:
    """
    Reciprocal Rank Fusion to combine semantic and BM25 ranked lists.

    For each candidate appearing in either list:
        score = sum(1 / (k + rank)) across both lists

    where rank is 1-based position in each list.

    Args:
        semantic_results: Ranked results from semantic search.
        bm25_results: Ranked results from BM25 search.
        k: RRF constant (default 60, standard value).

    Returns:
        list[tuple[str, float]]: Fused results sorted by RRF score descending.
    """
    rrf_scores: dict[str, float] = {}

    # Score from semantic search (rank is 1-based)
    for rank, (cid, _score) in enumerate(semantic_results, start=1):
        rrf_scores[cid] = rrf_scores.get(cid, 0.0) + 1.0 / (k + rank)

    # Score from BM25 search (rank is 1-based)
    for rank, (cid, _score) in enumerate(bm25_results, start=1):
        rrf_scores[cid] = rrf_scores.get(cid, 0.0) + 1.0 / (k + rank)

    # Sort by fused score descending
    fused = sorted(rrf_scores.items(), key=lambda x: x[1], reverse=True)
    return fused


def hybrid_search(
    query_text: str,
    faiss_index: faiss.Index,
    bm25_index: BM25Okapi,
    candidate_ids: list[str],
    model: SentenceTransformer,
    faiss_top_k: int = 500,
    bm25_top_k: int = 500,
    rrf_k: int = 60,
) -> list[tuple[str, float]]:
    """
    Run the full hybrid retrieval pipeline.

    1. Semantic search via FAISS
    2. Keyword search via BM25
    3. Fuse results with Reciprocal Rank Fusion
    4. Return top-500 candidates

    Args:
        query_text: The JD or search query text.
        faiss_index: Pre-built FAISS index.
        bm25_index: Pre-built BM25Okapi index.
        candidate_ids: Ordered candidate IDs.
        model: Loaded SentenceTransformer model.
        faiss_top_k: Number of results from FAISS.
        bm25_top_k: Number of results from BM25.
        rrf_k: RRF constant.

    Returns:
        list[tuple[str, float]]: Top-500 fused (candidate_id, rrf_score) pairs.
    """
    # Run both retrieval methods
    sem_results = semantic_search(
        query_text, faiss_index, candidate_ids, model, top_k=faiss_top_k
    )
    bm25_results = bm25_search(
        query_text, bm25_index, candidate_ids, top_k=bm25_top_k
    )

    # Fuse and return top-500
    fused = rrf_fusion(sem_results, bm25_results, k=rrf_k)
    return fused[:config.RRF_TOP_K]
