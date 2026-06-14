import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Независимый public-модуль — не dispatcher layout.
export default defineConfig({
  plugins: [react()],
  base: '/',
})
