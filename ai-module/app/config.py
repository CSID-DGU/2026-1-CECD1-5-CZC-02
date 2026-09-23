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

# LLM provider
# ollama: 로컬 Ollama만 사용 (기본값, 무료)
# openai: OpenAI 우선, 실패·예산 초과 시 Ollama로 자동 전환. OPENAI_API_KEY가 없으면 Ollama만 사용한다.
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama").lower()
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL_ANALYSIS = os.getenv("OPENAI_MODEL_ANALYSIS", "gpt-5.6-luna")
OPENAI_MODEL_REPLY = os.getenv("OPENAI_MODEL_REPLY", "gpt-5.6-terra")
OPENAI_TIMEOUT_SECONDS = float(os.getenv("OPENAI_TIMEOUT_SECONDS", "30"))
# 서버 프로세스 기준 월 누적 예상 비용 상한(USD). 재시작 시 초기화되므로 OpenAI 대시보드 예산 한도도 함께 설정할 것.
OPENAI_MONTHLY_BUDGET_USD = float(os.getenv("OPENAI_MONTHLY_BUDGET_USD", "10"))
# 비워두면 전송하지 않는다. 예: low, medium
OPENAI_REASONING_EFFORT = os.getenv("OPENAI_REASONING_EFFORT", "").strip()

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
