"""이력서 → 인력 후보 프로필 (resume_profile). 표는 규칙, 줄글·남은 칸만 로컬 LLM."""
from app.resumes.engine import ResumeResult, extract_resume

__all__ = ["ResumeResult", "extract_resume"]
