# Intelligent Candidate Discovery — Task Tracker

## Phase 1: Ranking Engine Foundation ✅ COMPLETE
- [x] Set up Python project structure with dependencies
- [x] Create data models (Candidate dataclass)
- [x] Build candidate data loader (stream `candidates.jsonl`)
- [x] Parse JD → hardcode structured intent + expanded skills
- [x] Implement feature extraction pipeline (all 6 categories, ~40 features)
- [x] Implement honeypot detection rules (5 checks)
- [x] Implement disqualifier filters (consulting-only, title-chasers, etc.)

## Phase 2: Retrieval & Ranking ✅ COMPLETE
- [x] Pre-compute candidate embeddings (BGE-small-en-v1.5, 384-dim)
- [x] Build FAISS IndexFlatIP from embeddings (100K vectors)
- [x] Implement BM25 search with rank-bm25
- [x] Implement RRF hybrid fusion
- [x] Implement weighted scoring model (8 score components)
- [x] Build main `rank.py` orchestrator
- [x] Generate reasonings for top 100 (template-based)
- [x] Produce `submission.csv` and validate ✅ passes validate_submission.py

## Phase 3: Optimize & Validate ✅ COMPLETE
- [x] Profile runtime — 63s total, within 5-min limit (237s spare)
- [x] Fix OpenMP crash: added OMP_NUM_THREADS=1, MKL_NUM_THREADS=1
- [x] BM25 optimization: dedup tokens, limit to 60, argpartition
- [x] Honeypot audit: expanded NON_TECH_TITLE_KEYWORDS (80+ entries)
- [x] Added CLEARLY_NON_ML_TITLES for aggressive trap detection
- [x] Lowered title_skill_mismatch threshold 8→3
- [x] Manual review top-20 — zero traps, all ML/AI/Search engineers
- [x] Optimize memory usage — Peak memory footprint is ~2.8 GB (limit 16 GB)
- [x] Create Dockerfile for sandboxed reproduction — fixed OpenMP and env vars

## Phase 4: Web App (Sandbox + Demo)
- [x] Initialize Spring Boot + Kotlin/JS project — Gradle multi-module (`webapp/`), Java 21 + Spring Boot 3.3 backend, Kotlin 2.4/JS + React frontend, wrapper committed
- [x] Set up Docker Compose — `webapp/docker-compose.yml` + multi-stage `Dockerfile` (JRE 21 + self-contained ranker venv)
- [x] Build candidate ingestion pipeline — `ProfileService` streams `candidates.jsonl` once, caches the 100 ranked profiles (~5s, low MB); `ResultsService` reads `submission.csv`; `JdService` parses `jd_intent.json`
- [x] Build search → results flow — REST API (`/api/jd`, `/api/results`, `/api/candidates/{id}`, `/api/status`) + `MatchService` display-only fit breakdown
- [x] Build interactive UI — React: JD panel, search → ranked cards, profile drawer (career/skills/signals + AI reasoning + match bars), live **Re-run Ranking** console streaming Python logs over SSE
- [x] Deploy sandbox — single deployable jar (SPA bundled as Spring static); verified e2e on :8080; live re-run ran `ranker.rank` to completion (72s, exit 0) and reproduced a valid `submission.csv`. Engine code untouched.

## Phase 5: Submission Package
- [ ] Prepare GitHub repo with clean README
- [ ] Fill `submission_metadata.yaml`
- [ ] Verify sandbox link works
- [ ] Final validation + submit

---

## Key Metrics
- **Pipeline Runtime**: ~63s (limit: 300s) — **237s remaining** ✅
- **Filtering**: 180/500 candidates removed (honeypots + disqualified)
- **Score Range**: 0.720 – 0.875
- **Top-10 Quality**: All ML/AI/Search engineers at product companies (Zomato, Salesforce, Flipkart, Netflix, upGrad, Amazon, CRED, Freshworks)
- **Traps in Top-20**: 0 ✅

## Known Issues
- ~~BM25 search was the bottleneck (263s)~~ **FIXED**: optimized to 55s via token dedup + limit
- Scoring rubric: 0.5·NDCG@10 + 0.3·NDCG@50 + 0.15·MAP + 0.05·P@10
