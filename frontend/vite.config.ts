import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'pwa-icon.svg'],
      manifest: false,
      workbox: {
        navigateFallback: '/index.html',
        runtimeCaching: [
          {
            urlPattern: ({ request }) => request.destination === 'document',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'app-shell',
              networkTimeoutSeconds: 3,
            },
          },
          {
            urlPattern: ({ request }) =>
              request.destination === 'script' ||
              request.destination === 'style' ||
              request.destination === 'image' ||
              request.destination === 'font',
            handler: 'CacheFirst',
            options: {
              cacheName: 'static-assets',
              expiration: {
                maxEntries: 200,
                maxAgeSeconds: 60 * 60 * 24 * 30,
              },
            },
          },
        ],
      },
    }),
  ],
  build: {
    sourcemap: process.env.VITE_SOURCEMAP === 'true',
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/shared/utils/test-setup.ts',
    globals: true,
    exclude: ['e2e/**', 'node_modules/**'],
    // Slow machines / CI: reduce flaky worker handshakes (Vitest 4: avoid deprecated poolOptions).
    hookTimeout: 120_000,
    teardownTimeout: 120_000,
    testTimeout: 30_000,
    maxWorkers: 4,
  },
})
