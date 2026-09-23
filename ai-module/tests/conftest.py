import os
import sys
from pathlib import Path

# 테스트는 로컬 .env 설정과 무관하게 규칙 엔진·유료 API 비활성 상태로 실행한다.
os.environ["AI_ENGINE"] = "rule"
os.environ["LLM_PROVIDER"] = "ollama"
os.environ.pop("OPENAI_API_KEY", None)
os.environ["EMBEDDING_CLASSIFIER_ENABLED"] = "false"
os.environ["GLINER_ENABLED"] = "false"

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
