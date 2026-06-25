<p align="center" style="text-align: center;">
  <a href="https://github.com/mrmayankmathur/intelligent_candidate_discovery">
    <h1>Intelligent Candidate Discovery</h1>
  </a>
</p>
<p align="center" style="margin-bottom: 4px">The intelligent, constraint-aware candidate ranking engine.</p>
<p align="center">
  <a href="https://github.com/mrmayankmathur/intelligent_candidate_discovery"><img alt="Python" src="https://img.shields.io/badge/Python-3.10-blue?style=flat-square&logo=python" /></a>
  <a href="https://github.com/mrmayankmathur/intelligent_candidate_discovery"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-JS-purple?style=flat-square&logo=kotlin" /></a>
  <a href="https://github.com/mrmayankmathur/intelligent_candidate_discovery"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.3-green?style=flat-square&logo=spring" /></a>
</p>

### Frontend UI Previews

<div style="text-align: center;">
  <img src="assets/ui-1.png" alt="Candidate Dashboard">
  <br>
  <img src="assets/ui-2.png" alt="Live Ranking UI">
  <br>
  <img src="assets/ui-3.png" alt="Candidate AI Profile Deep Dive">
</div>



### Architecture

<img src="assets/Architecture_Flow.png" alt="Architecture Flow">

---

### Installation

```bash
# Clone the repository
git clone https://github.com/mrmayankmathur/intelligent_candidate_discovery && cd intelligent_candidate_discovery

# Fetch precomputed artifacts and the baked embedding model
git lfs install && git lfs pull        

# Install dependencies (only needed for local non-Docker runs)
pip install -r ranker/requirements.txt
```

> [!TIP]
> Pre-computation is already done. The embeddings, FAISS index, and BM25 corpus are checked into the repo via Git LFS.

### Reproducing the Submission

The judged ranking step runs CPU-only with **no network**. Build once, then run:

```bash
docker build -t icd-ranker -f ranker/Dockerfile .

mkdir -p output
docker run --rm \
  --memory=16g \
  --network=none \
  -v $(pwd)/dataset:/data:ro \
  -v $(pwd)/output:/output \
  icd-ranker

# Validate the result (must be exactly 100 rows)
python dataset/validate_submission.py output/submission.csv
```

### Web UI Sandbox

The interactive visual frontend lets you explore candidate profiles and AI-generated reasoning. To run the sandbox locally:

```bash
cd webapp
./gradlew :backend:bootRun
```

Then navigate to `http://localhost:8080`.

### Directory Structure

- `ranker/`: The core Python ranking engine.
- `webapp/`: The Spring Boot + Kotlin/JS sandbox demo.
- `dataset/`: Hackathon data files.
- `submission.csv`: The final generated submission output.
