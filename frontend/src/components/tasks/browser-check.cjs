/* Optional browser smoke test: NODE_PATH=<playwright 설치 경로>/node_modules node .../browser-check.cjs */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  const url = process.env.TASK_PREVIEW_URL || 'http://127.0.0.1:5175/src/components/tasks/preview.html';
  try {
    await page.goto(url);
    await page.getByRole('heading', { name: '할 일 확인과 관리' }).waitFor();
    await page.getByRole('button', { name: '확정하기', exact: true }).click();
    await page.getByRole('button', { name: '완료하기', exact: true }).first().waitFor();
    assert.equal(await page.getByRole('button', { name: '확정하기', exact: true }).count(), 0);
    await page.getByLabel('종류 필터').selectOption('MEETING_PREP');
    assert.equal(await page.getByLabel('할 일 내용', { exact: true }).count(), 1);
    await page.getByLabel('종류 필터').selectOption('');
    await page.getByLabel('할 일 내용', { exact: true }).first().fill('계약서 초안 수정 송부');
    await page.getByRole('button', { name: '수정 저장', exact: true }).first().click();
    assert.equal(await page.getByText(/체험 수정 기록 2건/).count(), 1);
    await page.getByRole('button', { name: '완료하기', exact: true }).first().click();
    await page.getByRole('button', { name: '다시 열기', exact: true }).click();
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false);

    // 실제 TasksPage를 사용하고 HTTP만 가짜로 대체: ETag, 409, 수정 유지, 재시도 검증.
    let task = { taskId: 't_1', noteId: 'mn_1', content: '계약서 송부', dueDate: null,
      dueDateIsDefault: false, assignee: 'user_demo', counterpartPersonId: null, commitmentKind: 'EXPLICIT',
      taskType: 'CONTRACT_PREP', taskTypeConfidence: 1, taskTypeSource: 'RULE', evidence: '계약서를 보내기로 함', status: 'DRAFT' };
    let conflict = true;
    let version = 1;
    await page.route('http://localhost:8080/api/meeting-notes', route => route.fulfill({ status: 200, headers: { 'access-control-allow-origin': '*' }, json: { success: true, data: [] } }));
    await page.route('http://localhost:8080/api/auth/me', route => route.fulfill({ status: 200, headers: { 'access-control-allow-origin': '*' }, json: { success: true, data: { id: 1 } } }));
    await page.route('http://localhost:8080/api/tasks**', async route => {
      const request = route.request();
      const headers = { 'access-control-allow-origin': '*', 'access-control-expose-headers': 'ETag', etag: `"version${version}"` };
      if (request.method() === 'PUT') {
        assert.equal(request.headers()['if-match'], `"version${version}"`);
        if (conflict) { conflict = false; version = 2; task = { ...task, content: '다른 기기에서 고친 계약서 송부' }; return route.fulfill({ status: 409, headers, json: { success: false, message: '변경 충돌입니다. 새로고침 후 다시 확인하세요.' } }); }
        task = request.postDataJSON(); version += 1;
      }
      await route.fulfill({ status: 200, headers, json: { success: true, data: request.url().endsWith('/api/tasks') ? [task] : task } });
    });
    await page.getByRole('button', { name: '내 할 일', exact: true }).click();
    await page.getByLabel('할 일 내용', { exact: true }).fill('수정한 계약서 송부');
    await page.getByRole('button', { name: '수정 저장', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: '변경 충돌' }).waitFor();
    assert.equal(await page.getByLabel('할 일 내용', { exact: true }).inputValue(), '수정한 계약서 송부');
    await page.getByRole('button', { name: '새로고침', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="할 일 내용"]')?.value === '다른 기기에서 고친 계약서 송부');
    await page.getByLabel('할 일 내용', { exact: true }).fill('수정한 계약서 송부');
    await page.getByRole('button', { name: '수정 저장', exact: true }).click();
    await page.getByRole('status').filter({ hasText: '수정 기록과 함께 저장' }).waitFor();
    assert.equal(task.content, '수정한 계약서 송부');
    assert.equal(task.evidence, '계약서를 보내기로 함');
    assert.deepEqual(errors, []);
    console.log('PASS: confirm/filter/audit/complete/reopen/mobile, live page ETag/409/edit retention/retry');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
