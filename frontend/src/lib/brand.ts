/**
 * Marka renkleri — tailwind.config.js `theme.extend.colors.brand` ile birebir
 * aynı kalmalı (tek istisna kaynak; başka yerde ham hex yazılmaz).
 *
 * JS tarafında renk değeri isteyen nadir API'ler için (ör.
 * BackgroundGradientAnimation'ın `r, g, b` string prop'ları). Bileşenlerde
 * her zaman `brand-*` Tailwind sınıflarını tercih edin.
 */
export const BRAND = {
  navy: '#0B234A',
  blue: '#2E8FFF',
  teal: '#17D6C3',
  iris: '#8B8CFF',
  ice: '#A9E9FF',
} as const

/** '#RRGGBB' → 'r, g, b' (BackgroundGradientAnimation renk API'si). */
export function rgbTriplet(hex: string): string {
  const [r, g, b] = hexChannels(hex)
  return `${r}, ${g}, ${b}`
}

/** Markadan türetilmiş koyu/açık ton — yeni ham hex eklemeden gradyan ucu üretir. */
export function shade(hex: string, factor: number): string {
  const [r, g, b] = hexChannels(hex).map((c) =>
    Math.max(0, Math.min(255, Math.round(c * factor))),
  )
  return `rgb(${r}, ${g}, ${b})`
}

/** shade()'in ikizi: beyaza doğru karıştırır (0 = renk, 1 = beyaz).
 *  Gündüz göğünün soluk uçlarını ham hex yazmadan üretmek için. */
export function tint(hex: string, factor: number): string {
  const [r, g, b] = hexChannels(hex).map((c) => Math.round(c + (255 - c) * factor))
  return `rgb(${r}, ${g}, ${b})`
}

function hexChannels(hex: string): [number, number, number] {
  const value = hex.replace('#', '')
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ]
}
