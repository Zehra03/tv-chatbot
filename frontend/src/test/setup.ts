import { apiClient } from '@/api/client'

/**
 * Vitest ortak kurulumu (vite.config.ts → test.setupFiles).
 *
 * jsdom `window.matchMedia` sağlamaz; framer-motion (MotionConfig
 * reducedMotion="user") ve prefers-reduced-motion sorguları buna dokunur.
 * Eksikse eşleşmeyen (matches: false) bir stub takılır — testlerde animasyonlar
 * "hareket açık" varsayımıyla koşar.
 */

// Birim testler hermetiktir: geliştiricinin yerel .env'i VITE_API_BASE_URL ile
// gerçek backend'i gösteriyor olabilir (MSW kapalı dev akışı). Testlerde istekler
// her zaman göreli kalmalı ki MSW'nin path tabanlı handler'ları eşleşsin.
apiClient.defaults.baseURL = ''
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
