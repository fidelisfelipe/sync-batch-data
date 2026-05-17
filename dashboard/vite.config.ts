import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/dashboard/',
  build: {
    outDir: '../src/main/resources/static/dashboard',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/monitor': 'http://localhost:8080',
      '/api':     'http://localhost:8080',
    },
  },
});
