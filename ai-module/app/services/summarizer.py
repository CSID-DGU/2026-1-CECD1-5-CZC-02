from typing import Tuple


def generate_summary(text: str, activity_type: str) -> Tuple[str, float]:
    """규칙 기반으로 짧은 요약을 생성합니다."""
    summary = text[:50] + "..." if len(text) > 50 else text
    return summary, 0.5


def determine_todo_requirement(text: str, activity_type: str) -> Tuple[bool, str]:
    todo_keywords = {"회의", "미팅", "통화", "확인", "검토", "전달", "발송"}
    todo_required = any(keyword in text for keyword in todo_keywords)
    todo_content = f"{activity_type} 후속 처리" if todo_required else ""
    return todo_required, todo_content
