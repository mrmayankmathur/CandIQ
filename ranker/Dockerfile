# ─────────────────────────────────────────────────────────────
# Intelligent Candidate Discovery — Ranking Engine
# ─────────────────────────────────────────────────────────────
# Reproduces the ranking step in a sandboxed, CPU-only container.
#
# Build:
#   docker build -t icd-ranker -f ranker/Dockerfile .
#
# Run (with contest constraints):
#   docker run --rm \
#     --memory=16g \
#     --network=none \
#     -v $(pwd)/dataset:/data:ro \
#     -v $(pwd)/output:/output \
#     icd-ranker
#
# Constraints enforced:
#   - 16 GB RAM limit (--memory=16g)
#   - CPU only (no GPU, base image has no CUDA)
#   - No network access (--network=none)
#   - 5-minute wall-clock limit (pipeline self-reports timing)
# ─────────────────────────────────────────────────────────────

FROM python:3.10-slim AS base

# Prevent Python from writing .pyc files and enable unbuffered output.
# OpenMP env vars prevent FAISS + torch segfault on macOS/Linux.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    KMP_DUPLICATE_LIB_OK=TRUE \
    OMP_NUM_THREADS=1 \
    MKL_NUM_THREADS=1 \
    TOKENIZERS_PARALLELISM=false

WORKDIR /app

# ── Install system dependencies ──────────────────────────────
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

# ── Install Python dependencies (cached layer) ──────────────
COPY ranker/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# ── Copy application code ────────────────────────────────────
COPY ranker/ /app/ranker/

# ── Copy pre-computed artifacts ──────────────────────────────
# These must be generated BEFORE building the image
# by running: python -m ranker.precompute_all
COPY ranker/artifacts/ /app/ranker/artifacts/

# ── Create output and data directories ───────────────────────
RUN mkdir -p /output /data

# ── Health check ─────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=5s --retries=1 \
    CMD python -c "import ranker; print('ok')" || exit 1

# ── Entrypoint ───────────────────────────────────────────────
# Expects candidates.jsonl mounted at /data/candidates.jsonl
# Writes submission.csv to /output/submission.csv
ENTRYPOINT [ \
    "python", "-m", "ranker.rank", \
    "--candidates", "/data/candidates.jsonl", \
    "--out", "/output/submission.csv", \
    "--artifacts-dir", "/app/ranker/artifacts" \
]
