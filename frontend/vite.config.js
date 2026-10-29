import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发态：前端 5173，接口代理到 Spring Boot 8080
// 生产态：vite build 产出 dist/，由后端静态资源目录直接托管（单端口部署）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
