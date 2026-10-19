import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      proxy: {
        '/api': {
          target: backend,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: '../backend/src/main/resources/static',
      emptyOutDir: true,
    },
  };
});