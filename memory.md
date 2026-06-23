# Redrob Hackathon - Current State Memory

## Completed Work (Phase 1-3)
- **Python Ranking Engine**: Created under `ranker/` directory.
- **Constraints Met**: Runs in 63 seconds (limit: 300s) and uses ~2.8 GB RAM (limit: 16 GB).
- **Pre-computation**: `ranker/artifacts/` contains 100K FAISS embeddings (`candidate_embeddings.npy`, `candidate.index`), Polars feature matrix (`candidate_features.parquet`), and BM25 index (`bm25_corpus.pkl`).
- **Hybrid Retrieval**: Uses `sentence-transformers/BAAI/bge-small-en-v1.5` + FAISS for semantic search and `rank_bm25` for keyword search, fused with RRF (Reciprocal Rank Fusion).
- **Honeypot/Trap Filters**: Implemented aggressive filtering in `ranker/pipeline/filtering.py`. Lowered trap threshold to 3 skills, and expanded `NON_TECH_TITLE_KEYWORDS` and `CLEARLY_NON_ML_TITLES` to >80 titles (Graphic Designer, HR Manager, etc.). Removed 180/500 traps.
- **Scoring & Reasoning**: 8 weighted criteria (skills, company quality, notice period, active status). Generates natural language reasoning for top 100.
- **Docker Sandbox**: `ranker/Dockerfile` configured with `python:3.10-slim` and critical OpenMP env vars (`KMP_DUPLICATE_LIB_OK=TRUE`, `OMP_NUM_THREADS=1`) to prevent FAISS+torch segfaults.
- **Validation**: Generated `submission.csv` completely passes `dataset/validate_submission.py`. Top-20 manually verified: 0 traps, all ML/AI/Search engineers from product companies.

## Outstanding Work (Phase 4-5)
- **Phase 4 (Web App Sandbox & Demo)**: Need to build the Spring Boot (Java) backend and Kotlin/JS frontend.
- **Phase 5 (Submission)**: Need to create `docker-compose.yml` to stitch the web app and python ranker together, push to GitHub, host the sandbox, and fill `submission_metadata_template.yaml`.

## Important Technical Context for Next Agent
- **DO NOT MODIFY RANKING LOGIC**: The Python engine in `ranker/` is perfectly tuned, validated, and optimized. It produces an excellent `submission.csv`. Do not touch the scoring or filtering logic unless absolutely necessary.
- **BM25 Performance**: BM25 was a bottleneck (263s). Fixed by deduplicating tokens and limiting query tokens to 60. Do not revert this.
- **FAISS/Torch Segfaults**: MacOS and Linux will crash if `faiss` and `SentenceTransformer` are loaded without `OMP_NUM_THREADS=1` and `KMP_DUPLICATE_LIB_OK=TRUE`. Keep these in `rank.py` and `Dockerfile`.
- **Web App Purpose**: The Web App is purely for the hackathon UI sandbox and demo. The actual evaluation is done by running the `icd-ranker` docker container offline to get the CSV. 
