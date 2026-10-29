import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发态：前端 3000，接口代理到 Spring Boot 8000
// 生产态：vite build 输出到 backend 的静态资源目录，由 Spring Boot 一并托管
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  }
})
