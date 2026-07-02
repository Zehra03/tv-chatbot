import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/ — defineConfig vitest'ten gelir ki `test` alanı tiplensin.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Animasyonlu bileşenler jsdom render'ını yavaşlatıyor; paralel yük
    // altında 5s varsayılanı yalancı timeout üretiyordu.
    testTimeout: 10000,
  },
})
