// 확인 시간 측정 (목표: 건당 3분). 탭이 보이고 최근 1분 안에 조작이 있을 때만 센다.
export const IDLE_LIMIT_MS = 60_000;
export const FLUSH_EVERY_SECONDS = 15;
export const MAX_SECONDS_PER_REQUEST = 3600;

export function createReviewTimer(send, now = Date.now) {
  let lastActivity = now();
  let pending = 0;
  let sending = null;

  async function flush() {
    while (sending) {
      await sending;
    }
    if (pending <= 0) {
      return;
    }
    const seconds = Math.min(pending, MAX_SECONDS_PER_REQUEST);
    pending -= seconds;
    sending = Promise.resolve()
      .then(() => send(seconds))
      .catch(() => {
        // 측정 실패가 확인 작업을 막으면 안 된다. 다음 전송 때 다시 보낸다
        pending += seconds;
      })
      .finally(() => {
        sending = null;
      });
    await sending;
  }

  return {
    markActivity() {
      lastActivity = now();
    },
    tick(visible) {
      if (!visible || now() - lastActivity > IDLE_LIMIT_MS) {
        return;
      }
      pending += 1;
      if (pending % FLUSH_EVERY_SECONDS === 0) {
        flush();
      }
    },
    flush,
    pendingSeconds: () => pending,
  };
}
