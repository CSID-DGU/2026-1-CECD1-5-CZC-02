"""OpenAI API를 이용한 요약 및 분석"""
import json
from typing import Dict, Any, Optional
from openai import OpenAI, APIError
from app.config import OPENAI_API_KEY, OPENAI_MODEL


client = OpenAI(api_key=OPENAI_API_KEY)


def generate_summary(text: str, activity_type: str) -> Tuple[str, float]:
    """
    GPT를 이용한 텍스트 요약
    Returns: (summary, confidence)
    """
    if not OPENAI_API_KEY:
        # API 키 없으면 폴백 요약
        return _fallback_summary(text), 0.5
    
    try:
        prompt = f"""
다음 메시지를 요약해주세요. 한 문장으로 간단하게 요약해주세요.

메시지: {text}
활동 유형: {activity_type}

요약:
"""
        response = client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "당신은 비즈니스 메시지를 분석하고 요약하는 전문가입니다. 한국어로 간단명료하게 요약해주세요."
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            temperature=0.3,
            max_tokens=100
        )
        
        summary = response.choices[0].message.content.strip()
        confidence = 0.8  # GPT 응답의 신뢰도
        return summary, confidence
        
    except APIError as e:
        print(f"OpenAI API 오류: {e}")
        return _fallback_summary(text), 0.5
    except Exception as e:
        print(f"요약 생성 오류: {e}")
        return _fallback_summary(text), 0.5


def determine_todo_requirement(text: str, activity_type: str) -> Tuple[bool, str]:
    """
    TODO 생성 필요 여부 판정 및 내용 생성
    Returns: (todo_required, todo_content)
    """
    if not OPENAI_API_KEY:
        return _fallback_todo_requirement(text, activity_type)
    
    try:
        prompt = f"""
다음 메시지에서 TODO(할일) 생성 필요 여부를 판정해주세요.

메시지: {text}
활동 유형: {activity_type}

JSON 형식으로 답변해주세요:
{{
  "todo_required": true/false,
  "todo_content": "할일 내용"
}}

답변:
"""
        response = client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "당신은 비즈니스 메시지에서 TODO 항목을 추출하는 전문가입니다. JSON 형식으로만 응답하세요."
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            temperature=0.3,
            max_tokens=150
        )
        
        response_text = response.choices[0].message.content.strip()
        
        # JSON 파싱
        try:
            # ```json ... ``` 형식이면 제거
            if response_text.startswith("```"):
                response_text = response_text.split("```")[1]
                if response_text.startswith("json"):
                    response_text = response_text[4:]
                response_text = response_text.strip()
            
            parsed = json.loads(response_text)
            todo_required = parsed.get("todo_required", False)
            todo_content = parsed.get("todo_content", "")
            return todo_required, todo_content
        except json.JSONDecodeError:
            return _fallback_todo_requirement(text, activity_type)
        
    except APIError as e:
        print(f"OpenAI API 오류: {e}")
        return _fallback_todo_requirement(text, activity_type)
    except Exception as e:
        print(f"TODO 판정 오류: {e}")
        return _fallback_todo_requirement(text, activity_type)


def _fallback_summary(text: str) -> str:
    """간단한 폴백 요약"""
    if len(text) > 50:
        return text[:50] + "..."
    return text


def _fallback_todo_requirement(text: str, activity_type: str) -> Tuple[bool, str]:
    """폴백 TODO 판정"""
    # 규칙 기반 판정
    todo_keywords = {'진행', '작업', '확인', '검토', '처리', '완료', '수행'}
    todo_required = any(kw in text for kw in todo_keywords)
    
    if activity_type == "TASK":
        todo_required = True
    elif activity_type == "CALL":
        todo_required = False
    
    todo_content = f"{activity_type} 처리" if todo_required else ""
    return todo_required, todo_content


from typing import Tuple
