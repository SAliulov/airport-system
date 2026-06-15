import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// Базовый путь переопределяется через VITE_BASE для прода (nginx под /board/).
export default defineConfig({
  plugins: [react()],
  base: process.env.VITE_BASE || '/',
  resolve: {
    dedupe: ['react', 'react-dom'],
  },
})
