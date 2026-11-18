import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev the UI runs on :5173 and proxies the API to Spring Boot on :8080.
// `npm run build` writes the production bundle straight into Spring's static folder.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
});
