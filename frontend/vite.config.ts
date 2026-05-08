import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    sourcemap: process.env.VITE_SOURCEMAP === 'true',
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/shared/utils/test-setup.ts',
    globals: true,
    exclude: ['e2e/**', 'node_modules/**'],
  },
})
