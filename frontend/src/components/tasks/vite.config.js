import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { resolve } from 'node:path';

// C 화면 독립 빌드. 공용 App.jsx/메뉴/빌드 설정은 변경하지 않는다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { outDir: 'dist/tasks', rollupOptions: { input: resolve('src/components/tasks/preview.html') } },
});
