import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => ({
  base: mode === 'pages' ? `/${process.env.GITHUB_REPOSITORY?.split('/').pop() || 'tarime-hardware'}/` : '/',
  plugins: [react(), tailwindcss()],
  server: {
    host: '127.0.0.1', port: 5173, strictPort: true,
    proxy: {
      '/api': { target: `http://127.0.0.1:${process.env.FPA_BACKEND_PORT || '8080'}` },
      '/actuator': { target: `http://127.0.0.1:${process.env.FPA_BACKEND_PORT || '8080'}` },
    },
  },
  test: { environment: 'node', include: ['src/**/*.test.ts'], clearMocks: true },
}))
