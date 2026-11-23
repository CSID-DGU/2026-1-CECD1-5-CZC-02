import { useCallback, useEffect, useRef } from 'react';
import { recordMeetingNoteReviewTime } from '../../api/meetingNotes';
import { createReviewTimer } from './reviewTimer';

const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'scroll'];
const CONFIRM_WAIT_MS = 1500;

/** 확인 대기 중인 회의록을 보는 동안 시간을 잰다. 돌려주는 함수는 확정 직전에 남은 시간을 보낸다. */
export function useReviewTimer(noteId, enabled) {
  const timerRef = useRef(null);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    const timer = createReviewTimer((seconds) => recordMeetingNoteReviewTime(noteId, seconds));
    timerRef.current = timer;
    const onActivity = () => timer.markActivity();
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        timer.flush();
      } else {
        timer.markActivity();
      }
    };
    const options = { capture: true, passive: true };
    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, onActivity, options));
    document.addEventListener('visibilitychange', onVisibility);
    const intervalId = window.setInterval(() => timer.tick(document.visibilityState === 'visible'), 1000);

    return () => {
      window.clearInterval(intervalId);
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, onActivity, options));
      document.removeEventListener('visibilitychange', onVisibility);
      timer.flush();
      timerRef.current = null;
    };
  }, [noteId, enabled]);

  // 측정 때문에 확정이 늦어지지 않게 오래 기다리지 않는다
  return useCallback(() => Promise.race([
    timerRef.current?.flush() ?? Promise.resolve(),
    new Promise((resolve) => { window.setTimeout(resolve, CONFIRM_WAIT_MS); }),
  ]), []);
}
