import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE_URL || ''
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [
      vue(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: apiBase
        ? undefined
        : {
            '/api': {
              target: devProxyTarget,
              changeOrigin: true,
            },
          },
    },
    build: {
      target: 'es2022',
      sourcemap: false,
      chunkSizeWarningLimit: 900,
    },
  }
})
