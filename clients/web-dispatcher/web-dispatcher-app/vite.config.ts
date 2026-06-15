import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// Сборка и dev обслуживаются под префиксом /dispatcher/ (см. nginx.conf).
export default defineConfig({
  plugins: [react()],
  base: '/dispatcher/',
  resolve: {
    dedupe: ['react', 'react-dom'],
  },
})
