# ─────────────────────────────────────────────────────────────────────────────
# Redrob Intelligent Candidate Discovery — web app (demo sandbox)
#
# Multi-stage:
#   1) build  — Gradle builds the Spring Boot jar (this also builds the Kotlin/JS
#               React frontend via the Kotlin toolchain and bundles it as static).
#   2) runtime — JRE 21 + a self-contained Python venv for the frozen ranker so the
#               "Re-run Ranking" button works inside the container.
#
# The Python engine code (ranker/) is mounted/owned read-only at runtime and is never
# modified. Heavy: the runtime installs torch/faiss/sentence-transformers (~several GB).
# For a lightweight local demo, prefer `./gradlew :backend:bootRun` (see README-webapp.md).
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: build the jar (frontend + backend) ──────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Copy the gradle wrapper + build scripts first for better layer caching.
COPY webapp/ /src/webapp/
WORKDIR /src/webapp
RUN ./gradlew --no-daemon :backend:bootJar

# ── Stage 2: runtime (JRE + Python venv) ─────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Build the ranker's Python environment (matches ranker/requirements.txt).
COPY ranker/requirements.txt /tmp/requirements.txt
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/venv/bin/pip install --no-cache-dir -r /tmp/requirements.txt

COPY --from=build /src/webapp/backend/build/libs/*.jar /app/app.jar

# Repo root + python are provided by docker-compose via env; dataset/ranker/submission
# are mounted as volumes there (they are large and stay outside the image).
ENV DISCOVERY_REPO_ROOT=/work
ENV DISCOVERY_PYTHON=/opt/venv/bin/python
ENV KMP_DUPLICATE_LIB_OK=TRUE
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
