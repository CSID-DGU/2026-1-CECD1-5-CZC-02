import { useState } from 'react';
import { PeoplePage } from './PeoplePage.jsx';
import { peoplePreviewApi } from './peoplePreview.js';

// 공용 DashboardLayout 안에 끼우는 사람 화면. 별도 메뉴를 중복 렌더하지 않는다.
export function PeopleScreen() {
  const [preview, setPreview] = useState(false);
  return <PeoplePage key={preview ? 'preview' : 'live'} apiClient={preview ? peoplePreviewApi : undefined}
    preview={preview} onPreviewToggle={() => setPreview(value => !value)} />;
}
