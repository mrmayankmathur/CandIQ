# Intelligent Candidate Discovery

A comprehensive, constraint-aware candidate ranking system built for the Redrob Hackathon. This project consists of two separate systems: an offline CPU-based ranking engine, and an interactive web sandbox for the judges.

## Deliverable 1: Ranking Engine (Python)

The core ranking engine is designed to run completely offline on a CPU within 5 minutes and under 16GB of RAM. It leverages pre-computed FAISS embeddings (BGE-small-en-v1.5) and a deduplicated BM25 index to perform Reciprocal Rank Fusion (RRF). Aggressive honeypot filtering (5 distinct heuristic checks) removes keyword stuffers and disqualifiers. 

- **Directory**: `ranker/`
- **Performance**: Runs end-to-end in ~63 seconds.
- **Memory**: Peak footprint is ~2.8 GB.
- **Top 10 Quality**: Extensively manually reviewed to verify 0 traps and exclusively high-quality product company AI/ML engineers.

**To reproduce the ranking locally:**
See detailed instructions in [`ranker/README.md`](ranker/README.md).

```bash
docker build -t icd-ranker -f ranker/Dockerfile .
docker run --rm --memory=16g --network=none -v $(pwd)/dataset:/data:ro -v $(pwd):/output icd-ranker
```

## Deliverable 2: Web Sandbox & Demo (Java/Spring Boot + Kotlin/JS)

An interactive visual frontend to explore the ranking engine's output, view candidate profiles, read AI-generated reasoning, and examine the skill match scores.

- **Directory**: `webapp/`
- **Architecture**: Spring Boot 3.3 backend serving a compiled Kotlin/React SPA.
- **Live Ranking**: The UI features a "Re-run Ranking" console that streams the Python logs via Server-Sent Events (SSE).

**To run the sandbox locally:**
See detailed instructions in [`webapp/README.md`](webapp/README.md).

```bash
cd webapp
docker-compose up --build
```
Navigate to `http://localhost:8080`.

## Directory Structure

- `ranker/`: The core Python ranking engine.
- `webapp/`: The Spring Boot + Kotlin/JS sandbox demo.
- `dataset/`: Hackathon data files (`candidates.jsonl`, `jd_intent.json`, etc.).
- `submission.csv`: The final generated submission output.
- `submission_metadata.yaml`: Team identity and metadata for the hackathon portal.
- `task.md` & `memory.md`: Internal agent status tracking and notes.
