import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Сборка и dev обслуживаются под префиксом /board/ (см. nginx.conf).
// https://vite.dev/config/shared-options.html#base
export default defineConfig({
  plugins: [react()],
  base: '/board/',
})
