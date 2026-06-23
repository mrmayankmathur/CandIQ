# Redrob — Intelligent Candidate Discovery · Web App (Phase 4)

An interactive demo over the **frozen Python ranking engine** (`../ranker`). Judges view the job
description, browse the engine's ranked shortlist, open full candidate profiles with the AI
reasoning, and can trigger a **live re-run** of the Python ranker with its logs streaming in.

> The Python engine (`ranker/`, `dataset/`, artifacts) is **final and never modified** by this app.
> The web app only *reads* `submission.csv`, `dataset/candidates.jsonl`, and
> `ranker/artifacts/jd_intent.json`, and *shells out* to the existing `python -m ranker.rank` CLI.

## Stack

| Layer    | Tech                                                                 |
|----------|----------------------------------------------------------------------|
| Backend  | Java 21 · Spring Boot 3.3 (REST + Server-Sent Events)                |
| Frontend | Kotlin 2.4 / JS · React (kotlin-wrappers) · emotion                  |
| Build    | Gradle (multi-module, wrapper committed)                             |

## Architecture

```
Browser (Kotlin/JS + React SPA)
   │  REST + SSE (JSON, same origin)
   ▼
Spring Boot (Java 21)
   ├─ ResultsService  → loads submission.csv (top-100) into memory
   ├─ ProfileService  → one streaming pass over candidates.jsonl, caches the ~100 ranked profiles
   ├─ JdService       → parses ranker/artifacts/jd_intent.json
   ├─ MatchService    → display-only fit bars (skill / experience / location / intent)
   └─ RunnerService   → spawns `python -m ranker.rank`, streams stdout over SSE, reloads on exit
```

Paths are auto-detected (the folder containing `submission.csv` + `ranker/`) and overridable with
`DISCOVERY_REPO_ROOT` / `DISCOVERY_PYTHON`. The live re-run sets `KMP_DUPLICATE_LIB_OK=TRUE` to
mirror the ranker's own faiss/torch OpenMP fix.

## Run locally (recommended)

Requires JDK 21 and the ranker's `.venv` already set up at the repo root.

```bash
cd webapp
./gradlew :backend:bootRun
# open http://localhost:8080
```

`bootRun` builds the Kotlin/JS frontend, bundles it into the jar's static resources, and serves the
whole app on **:8080**. Startup loads the JD, the 100 ranked rows, and caches the 100 profiles
(~5s, one pass over the 487MB JSONL).

### Frontend dev server (hot reload, optional)

```bash
./gradlew :frontend:jsBrowserDevelopmentRun   # http://localhost:8081, proxies /api to :8080
```

### Build a single deployable jar

```bash
./gradlew :backend:bootJar
java -jar backend/build/libs/backend-0.1.0.jar
```

## API

| Method | Path                       | Purpose                                            |
|--------|----------------------------|----------------------------------------------------|
| GET    | `/api/jd`                  | Parsed job description                              |
| GET    | `/api/results?limit=100`   | Ranked candidates (engine ranking + profile summary)|
| GET    | `/api/candidates/{id}`     | Full profile + AI reasoning + match breakdown      |
| GET    | `/api/status`              | Load time, count, last run status                  |
| POST   | `/api/rank/run`            | Start a live re-run (409 if already running)       |
| GET    | `/api/rank/stream` (SSE)   | `log` / `done` / `error` events from the run       |

## Docker (heavy, self-contained)

```bash
docker compose -f webapp/docker-compose.yml up --build
# http://localhost:8080
```

The image installs the ranker's Python deps (`torch`/`faiss`/`sentence-transformers`), so the first
build is large and slow; `dataset/`, `ranker/`, and `submission.csv` are mounted from the host. For a
quick demo, prefer the local `bootRun` path above.

## Verified

- `bootRun` → JD + 100 results + 100/100 profiles cached; SPA served at `/`.
- `/api/candidates/CAND_0018499` → full profile, reasoning, match breakdown (Noida = preferred, exp 1.0).
- **Live re-run** through the UI ran `python -m ranker.rank` to completion (~72s), streamed all logs,
  exited 0, hot-reloaded results, and reproduced an identical, valid `submission.csv`.
