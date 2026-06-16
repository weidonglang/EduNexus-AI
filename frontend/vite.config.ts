import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_DEV_PORT ?? 5173),
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return
            }
            if (id.includes('echarts') || id.includes('zrender')) {
              return 'vendor-echarts'
            }
            if (id.includes('element-plus') || id.includes('@element-plus')) {
              return 'vendor-element-plus'
            }
            if (id.includes('lucide-vue-next') || id.includes('@element-plus/icons-vue')) {
              return 'vendor-icons'
            }
            if (id.includes('vue') || id.includes('pinia')) {
              return 'vendor-vue'
            }
            if (id.includes('axios')) {
              return 'vendor-axios'
            }
            return 'vendor'
          },
        },
      },
    },
  }
})
