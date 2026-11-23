// 응답에 Google 이벤트 ID가 있을 때만 "등록됨"으로 말한다. 실패·데모·연동 꺼짐은 모두 ID가 없다.
export function calendarRegistrationResult(schedule) {
  if (schedule?.googleCalendarEventId) {
    const time = String(schedule.scheduleDateTime || '').slice(0, 16).replace('T', ' ');
    return {
      registered: true,
      message: time ? `캘린더에 등록되어 있습니다. 일시: ${time}` : '캘린더에 등록되어 있습니다.',
    };
  }
  return {
    registered: false,
    message: '앱에 일정을 저장했지만 캘린더에는 등록되지 않았습니다. 연동이 꺼져 있거나 데모 계정이면 앱에만 표시됩니다.',
  };
}
