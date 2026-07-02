/**
 * Vitest ortak kurulumu (vite.config.ts → test.setupFiles).
 *
 * jsdom `window.matchMedia` sağlamaz; framer-motion (MotionConfig
 * reducedMotion="user") ve prefers-reduced-motion sorguları buna dokunur.
 * Eksikse eşleşmeyen (matches: false) bir stub takılır — testlerde animasyonlar
 * "hareket açık" varsayımıyla koşar.
 */
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
