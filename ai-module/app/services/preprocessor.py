"""텍스트 전처리 모듈"""
import re


def preprocess_text(text: str) -> str:
    """
    텍스트 전처리
    - 특수문자 제거 (단, 괄호, 하이픈, 슬래시 유지)
    - 공백 정규화
    """
    if not text:
        return ""
    
    # 특수문자 제거 (알파벳, 한글, 숫자, 괄호, 하이픈, 슬래시, 콜론만 유지)
    text = re.sub(r'[^\w\s\(\)\-/:시분일월년오전오후]', '', text, flags=re.UNICODE)
    
    # 여러 공백을 하나로 정규화
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text
