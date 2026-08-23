import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
// Сборка и dev обслуживаются под префиксом /dispatcher/ (см. nginx.conf).
export default defineConfig({
  plugins: [react()],
  base: '/dispatcher/',
  server: {
    // 'localhost' резолвится Node в ::1 (IPv6-only) и браузер может не достучаться
    // по 127.0.0.1 — слушаем все интерфейсы, чтобы работали оба варианта.
    host: true,
  },
  resolve: {
    dedupe: ['react', 'react-dom'],
    alias: {
      // clients/shared лежит вне node_modules-цепочки этого приложения (не workspace), поэтому его импорты сторонних пакетов резолвим сюда явно.
      '@stomp/stompjs': path.resolve(dirname, 'node_modules/@stomp/stompjs'),
      'sockjs-client': path.resolve(dirname, 'node_modules/sockjs-client'),
    },
  },
})
