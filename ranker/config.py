"""
Configuration for the Intelligent Candidate Discovery ranking engine.

Paths, constants, and tunable parameters for the ranking pipeline.
"""

from pathlib import Path
import os

# ─── Project Paths ──────────────────────────────────────────────────────────────

PROJECT_ROOT = Path(__file__).parent.parent
RANKER_ROOT = Path(__file__).parent
DATASET_DIR = PROJECT_ROOT / "dataset"
ARTIFACTS_DIR = RANKER_ROOT / "artifacts"

# Input files
CANDIDATES_JSONL = DATASET_DIR / "candidates.jsonl"
SAMPLE_CANDIDATES_JSON = DATASET_DIR / "sample_candidates.json"
JD_INTENT_FILE = ARTIFACTS_DIR / "jd_intent.json"

# Pre-computed artifact paths
EMBEDDINGS_FILE = ARTIFACTS_DIR / "candidate_embeddings.npy"
FAISS_INDEX_FILE = ARTIFACTS_DIR / "candidate.index"
FEATURES_FILE = ARTIFACTS_DIR / "candidate_features.parquet"
CANDIDATE_IDS_FILE = ARTIFACTS_DIR / "candidate_ids.json"
HONEYPOT_FLAGS_FILE = ARTIFACTS_DIR / "honeypot_flags.json"
BM25_CORPUS_FILE = ARTIFACTS_DIR / "bm25_corpus.pkl"

# Output
SUBMISSION_CSV = PROJECT_ROOT / "submission.csv"

# ─── OpenAI (pre-computation only) ──────────────────────────────────────────────

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
OPENAI_MODEL = "gpt-4o"  # For JD parsing and reasoning generation

# ─── Embedding Model ────────────────────────────────────────────────────────────

EMBEDDING_MODEL_NAME = "BAAI/bge-small-en-v1.5"  # 384-dim, good quality, CPU-fast
EMBEDDING_DIM = 384
EMBEDDING_BATCH_SIZE = 128
EMBEDDING_MAX_SEQ_LENGTH = 160  # Cap tokens for CPU throughput (default 512 is ~4x slower)

# ─── Retrieval Parameters ───────────────────────────────────────────────────────

# FAISS ANN search
FAISS_TOP_K = 500        # Top K from semantic search
FAISS_NPROBE = 32        # IVF nprobe (if using IVF index)

# BM25 keyword search
BM25_TOP_K = 500         # Top K from keyword search

# RRF Hybrid Fusion
RRF_K = 60               # RRF constant (standard value)
RRF_TOP_K = 500          # Combined top K after fusion

# ─── Scoring Weights ────────────────────────────────────────────────────────────
# These weights determine the final candidate score.
# Tuned based on JD analysis — NDCG@10 is 50% of hackathon score,
# so skill match and career fit are weighted highest.

SCORE_WEIGHTS = {
    "skill_match":          0.25,   # Skill alignment with JD
    "experience_fit":       0.15,   # Years + domain experience match
    "career_quality":       0.20,   # Product company, tenure, trajectory
    "behavioral_signals":   0.15,   # Activity, response rate, availability
    "education":            0.05,   # Tier, degree relevance
    "location_fit":         0.05,   # Pune/Noida/India preference
    "semantic_similarity":  0.10,   # Embedding-based JD-candidate similarity
    "keyword_relevance":    0.05,   # BM25-based keyword overlap
}

# ─── Disqualifier & Filter Config ───────────────────────────────────────────────

# Companies that count as "consulting/services firms" (JD explicitly rejects
# candidates whose ENTIRE career is at these companies)
CONSULTING_FIRMS = {
    "tcs", "tata consultancy services", "infosys", "wipro", "accenture",
    "cognizant", "capgemini", "hcl", "hcl technologies", "tech mahindra",
    "mindtree", "mphasis", "l&t infotech", "lti", "ltimindtree",
    "persistent systems", "hexaware", "zensar", "cyient", "birlasoft",
    "niit technologies", "coforge",
}

# Non-tech roles that are disqualifiers (Marketing Manager with AI skills = trap)
# EXPANDED: smoke test revealed Graphic Designer, Mechanical Engineer, Project Manager
# leaking into top-10. These are keyword-stuffed honeypots.
NON_TECH_TITLE_KEYWORDS = {
    # Original entries
    "marketing manager", "content writer", "sales", "hr manager",
    "human resources", "accountant", "finance manager", "operations manager",
    "business development", "relationship manager", "recruiter",
    "administrative", "receptionist", "customer support",
    # Design / Creative (Graphic Designer was rank 4 in smoke test!)
    "graphic designer", "ui designer", "ux designer", "visual designer",
    "interior designer", "fashion designer", "creative director",
    "art director", "photographer", "videographer", "animator",
    "illustrator", "copywriter", "content strategist",
    # Non-engineering titles (Project Manager was rank 10!)
    "project manager", "program manager", "scrum master",
    "product owner", "delivery manager", "engagement manager",
    "account manager", "client partner", "customer success",
    # Mechanical / Civil / Non-CS engineering (Mechanical Engineer was rank 6!)
    "mechanical engineer", "civil engineer", "electrical engineer",
    "chemical engineer", "biomedical engineer", "environmental engineer",
    "structural engineer", "aerospace engineer", "mining engineer",
    "industrial engineer", "manufacturing engineer", "process engineer",
    "quality engineer", "safety engineer", "materials engineer",
    "petroleum engineer", "nuclear engineer", "agricultural engineer",
    # Finance / Legal / Admin
    "chartered accountant", "ca ", "financial analyst", "investment",
    "legal", "lawyer", "advocate", "paralegal", "compliance",
    "audit", "treasury", "tax manager", "controller",
    # Healthcare / Pharma
    "doctor", "nurse", "pharmacist", "physiotherapist",
    "medical officer", "clinical", "healthcare",
    # Education
    "teacher", "professor", "lecturer", "trainer", "tutor",
    "principal", "dean", "librarian",
    # Supply Chain / Logistics
    "supply chain", "logistics", "warehouse", "procurement",
    "purchase manager", "inventory", "store manager",
    # Real Estate / Construction
    "real estate", "property", "construction manager", "site engineer",
}

# Titles that are clearly NOT ML/AI/Software engineering roles —
# even if they have tech-sounding variants. Used for aggressive downranking.
CLEARLY_NON_ML_TITLES = {
    "graphic designer", "mechanical engineer", "civil engineer",
    "electrical engineer", "project manager", "scrum master",
    "chartered accountant", "teacher", "professor", "doctor",
    "nurse", "marketing manager", "sales manager", "hr manager",
    "content writer", "recruiter", "receptionist", "accountant",
    "interior designer", "fashion designer", "lawyer",
    "supply chain", "logistics", "warehouse",
}

# Pure research/academic titles (JD says no pure research without production)
PURE_RESEARCH_TITLES = {
    "research scientist", "research fellow", "postdoctoral",
    "research associate", "phd candidate", "visiting researcher",
    "research intern", "lab assistant",
}

# CV/Speech/Robotics domains (JD says not without NLP/IR exposure)
NON_NLP_DOMAINS = {
    "computer vision", "image processing", "object detection",
    "speech recognition", "speech synthesis", "tts", "asr",
    "robotics", "autonomous", "self-driving", "lidar",
}

# Titles indicating no longer coding (JD: "hasn't written code in 18 months")
NON_CODING_TITLES = {
    "chief technology officer", "cto", "vp engineering",
    "vice president", "director of engineering", "engineering director",
}

# ─── JD Core Skill Categories ──────────────────────────────────────────────────
# Extracted from deep JD analysis. These drive skill matching.

JD_MUST_HAVE_SKILLS = {
    # Production embeddings experience
    "sentence-transformers", "sentence transformers", "embeddings",
    "text embeddings", "openai embeddings", "bge", "e5",
    "embedding", "vector embeddings", "dense retrieval",

    # Vector DB / hybrid search
    "pinecone", "weaviate", "qdrant", "milvus", "opensearch",
    "elasticsearch", "elastic search", "faiss", "vector database",
    "vector search", "hybrid search", "ann", "approximate nearest neighbor",

    # Python
    "python",

    # Evaluation frameworks
    "ndcg", "mrr", "map", "mean average precision", "a/b testing",
    "ab testing", "ranking evaluation", "information retrieval",
    "evaluation framework",
}

JD_NICE_TO_HAVE_SKILLS = {
    # LLM fine-tuning
    "lora", "qlora", "peft", "fine-tuning", "fine tuning", "finetuning",

    # Learning to rank
    "learning to rank", "xgboost", "lightgbm", "lambdamart",

    # HR-tech / marketplace
    "hr tech", "recruiting", "recruitment", "marketplace",
    "talent acquisition",

    # Distributed systems
    "distributed systems", "kubernetes", "docker", "microservices",
    "kafka", "ray", "dask",

    # Open source
    "open source", "open-source", "github",
}

JD_EXPANDED_SKILLS = {
    # Semantic expansions of core JD requirements
    "retrieval", "search", "ranking", "recommendation system",
    "recommender system", "information retrieval", "ir",
    "natural language processing", "nlp", "text mining",
    "machine learning", "ml", "deep learning", "neural networks",
    "transformers", "bert", "gpt", "llm", "large language model",
    "rag", "retrieval augmented generation",
    "reranking", "re-ranking", "cross encoder", "bi-encoder",
    "cosine similarity", "semantic search", "semantic similarity",
    "tf-idf", "bm25", "inverted index",
    "data pipeline", "etl", "feature engineering",
    "mlops", "model deployment", "model serving",
    "pytorch", "tensorflow", "huggingface", "hugging face",
    "scikit-learn", "sklearn",
    "api", "rest api", "fastapi", "flask", "django",
    "sql", "postgresql", "mongodb", "redis",
    "aws", "gcp", "azure", "cloud",
}

# ─── Behavioral Scoring Thresholds ──────────────────────────────────────────────

# Staleness: how many days since last_active to consider "inactive"
STALENESS_THRESHOLD_DAYS = 180     # 6 months → significant downweight
STALENESS_MODERATE_DAYS = 90       # 3 months → moderate downweight

# Recruiter response rate thresholds
RESPONSE_RATE_GOOD = 0.5           # >= 50% is good
RESPONSE_RATE_BAD = 0.15           # < 15% is very bad (JD explicitly mentions 5%)

# Notice period
NOTICE_PERIOD_IDEAL = 30           # <= 30 days is ideal (JD: "sub-30-day")
NOTICE_PERIOD_OK = 60              # <= 60 days is acceptable
NOTICE_PERIOD_HIGH = 90            # > 90 days is a concern

# Experience range from JD
EXPERIENCE_MIN = 4                 # JD says "5-9" but considers from 4
EXPERIENCE_MAX = 12                # Some flexibility above 9
EXPERIENCE_IDEAL_MIN = 5
EXPERIENCE_IDEAL_MAX = 9

# ─── Output Parameters ──────────────────────────────────────────────────────────

TOP_K_OUTPUT = 100                 # Final ranked output size
