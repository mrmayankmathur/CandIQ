# Intelligent Candidate Discovery - Ranking Engine

# OpenMP runtime guard — set before any submodule imports faiss / torch.
# faiss-cpu and torch each bundle their own libomp; on macOS the duplicate
# runtime segfaults (exit 139) when the FAISS index and torch's encode() are
# used in the same process. Pinning thread counts also keeps CPU runs
# deterministic. Applied here so every entry point (rank, precompute_all, or a
# standalone module) inherits it.
import os

# KMP_DUPLICATE_LIB_OK is the real crash fix (allows the duplicate libomp).
# We intentionally do NOT pin OMP_NUM_THREADS globally — torch needs all cores
# for embedding throughput. Thread counts are set per-task where it matters.
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
