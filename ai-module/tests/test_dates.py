"""상대 날짜는 분석 시각이 아니라 메일 발송 시각을 기준으로 해석해야 한다."""
import asyncio
from datetime import datetime, timezone

from app.schemas.request import MessageItem
from app.services.analyzer import analyze_message, extract_date

WEDNESDAY = datetime(2026, 9, 9, 10, 0)


def test_next_week_weekday_uses_reference_time():
    assert extract_date("다음 주 화요일 오전 10시에 미팅 가능하실까요?", WEDNESDAY) == "2026-09-15"


def test_this_week_weekday_uses_reference_time():
    assert extract_date("이번 주 금요일 오후 2시 괜찮으신가요?", WEDNESDAY) == "2026-09-11"


def test_month_day_without_year_uses_reference_year():
    assert extract_date("1월 5일 오후 3시에 뵙겠습니다", datetime(2027, 1, 2)) == "2027-01-05"


def test_timezone_aware_reference_is_converted_to_seoul():
    # 2026-09-08 23:00 UTC = 2026-09-09 08:00 KST (수요일)
    reference = datetime(2026, 9, 8, 23, 0, tzinfo=timezone.utc)
    assert extract_date("다음 주 화요일 미팅", reference) == "2026-09-15"


def test_reanalyzing_old_mail_keeps_original_date():
    """몇 주 뒤에 다시 분석해도 같은 메일은 같은 날짜로 해석돼야 한다."""
    body = "다음 주 화요일 오전 10시에 미팅 가능하실까요? 참석자는 저와 최지훈 대리입니다."
    message = MessageItem(direction="RECEIVED", senderName="김현우", senderEmail="kim@example.com",
                          sentAt=WEDNESDAY, content=body)

    result = asyncio.run(analyze_message(body, [message], analysis_mode="rule", reference_time=WEDNESDAY))

    assert result.schedule is not None
    assert (result.schedule.date, result.schedule.time) == ("2026-09-15", "10:00")
