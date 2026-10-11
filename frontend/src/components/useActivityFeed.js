import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { getActivity } from '../api/analysisJobs';

// 화면을 옮겨도 이미 본 알림을 다시 띄우지 않도록 마지막으로 본 이벤트 번호를 탭 단위로 기억한다.
const LAST_EVENT_KEY = 'activityLastEventId';
const TOAST_LIFETIME_MS = 6000;

export const ActivityContext = createContext({ job: null, dataVersion: 0, refresh: () => {} });

export function useActivity() {
  return useContext(ActivityContext);
}

// 서버에서 도는 AI 분석 작업의 진행률과 수집·분석 알림을 주기적으로 가져온다.
// dataVersion은 메일 수집이나 분석 진척이 있을 때 올라가서, 목록 화면이 다시 불러올 신호로 쓴다.
export function useActivityFeed() {
  const [job, setJob] = useState(null);
  const [toasts, setToasts] = useState([]);
  const [dataVersion, setDataVersion] = useState(0);
  const lastDoneRef = useRef(null);
  const wakeRef = useRef(null);

  const dismissToast = useCallback((id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const poll = useCallback(async () => {
    const stored = sessionStorage.getItem(LAST_EVENT_KEY);
    let since = stored === null ? null : Number(stored);

    let feed;
    try {
      feed = await getActivity(since ?? 0);
    } catch {
      return false;
    }

    // 서버가 재시작되면 이벤트 번호가 처음부터 다시 매겨진다.
    if (since !== null && feed.latestEventId < since) {
      since = null;
    }

    setJob(feed.job);
    const done = feed.job ? `${feed.job.startedAt}:${feed.job.done}` : null;
    if (lastDoneRef.current !== null && done !== lastDoneRef.current) {
      setDataVersion((version) => version + 1);
    }
    lastDoneRef.current = done;

    if (since === null) {
      sessionStorage.setItem(LAST_EVENT_KEY, String(feed.latestEventId));
      return Boolean(feed.job?.running);
    }

    const fresh = feed.events.filter((event) => event.id > since);
    if (fresh.length > 0) {
      sessionStorage.setItem(LAST_EVENT_KEY, String(fresh[fresh.length - 1].id));
      setToasts((prev) => [...prev, ...fresh].slice(-4));
      fresh.forEach((event) => window.setTimeout(() => dismissToast(event.id), TOAST_LIFETIME_MS));
      if (fresh.some((event) => event.type === 'COLLECTED')) {
        setDataVersion((version) => version + 1);
      }
    }
    return Boolean(feed.job?.running);
  }, [dismissToast]);

  useEffect(() => {
    let cancelled = false;
    let timerId;
    let generation = 0;

    // refresh()로 즉시 다시 조회할 때 기존 예약·진행 중인 조회가 두 번째 루프를 만들지 않도록 세대를 센다.
    const loop = async () => {
      const current = ++generation;
      window.clearTimeout(timerId);
      const running = await poll();
      if (cancelled || current !== generation) {
        return;
      }
      const delay = document.hidden ? 15000 : running ? 2500 : 8000;
      timerId = window.setTimeout(loop, delay);
    };

    wakeRef.current = loop;
    loop();
    return () => {
      cancelled = true;
      window.clearTimeout(timerId);
    };
  }, [poll]);

  const refresh = useCallback(() => wakeRef.current?.(), []);

  return { job, toasts, dataVersion, refresh, dismissToast };
}

