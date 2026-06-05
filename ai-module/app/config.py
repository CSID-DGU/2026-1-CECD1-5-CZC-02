import os
from dotenv import load_dotenv

load_dotenv()

# Server
SERVER_HOST = os.getenv("SERVER_HOST", "0.0.0.0")
SERVER_PORT = int(os.getenv("SERVER_PORT", 8000))

# Backend URL (for testing/integration)
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

# AI engine
# rule: existing rule-based analyzer only
# ollama: rule-based analyzer + local Ollama LLM enhancement with rule fallback
AI_ENGINE = os.getenv("AI_ENGINE", "rule").lower()
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:3b")
OLLAMA_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_TIMEOUT_SECONDS", "45"))
OLLAMA_TEMPERATURE = float(os.getenv("OLLAMA_TEMPERATURE", "0.1"))

# Embedding classifier
# Disabled by default so the app can still run without downloading model weights.
EMBEDDING_CLASSIFIER_ENABLED = os.getenv("EMBEDDING_CLASSIFIER_ENABLED", "false").lower() == "true"
EMBEDDING_MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-m3")
EMBEDDING_ACTION_THRESHOLD = float(os.getenv("EMBEDDING_ACTION_THRESHOLD", "0.52"))
EMBEDDING_BUSINESS_THRESHOLD = float(os.getenv("EMBEDDING_BUSINESS_THRESHOLD", "0.50"))

# Entity extractor
# Optional GLiNER extractor for customer/product/attendee hints.
GLINER_ENABLED = os.getenv("GLINER_ENABLED", "false").lower() == "true"
GLINER_MODEL_NAME = os.getenv("GLINER_MODEL_NAME", "urchade/gliner_multi-v2.1")
GLINER_THRESHOLD = float(os.getenv("GLINER_THRESHOLD", "0.35"))

# Timezone
TIMEZONE = "Asia/Seoul"
