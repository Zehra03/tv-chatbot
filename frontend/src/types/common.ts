/**
 * Ortak domain primitifleri. Diğer tip dosyaları (search/product/reservation/chat)
 * bunlardan türer; backend domain katmanı (proje dokümanı §7.4) ile hizalıdır.
 */

/** Ürün tipi — otel ya da uçuş. Arama, sonuç, taslak ve rezervasyon akışlarında ortak. */
export type ProductType = 'hotel' | 'flight'

/** ISO 8601 gün (YYYY-MM-DD) — otel giriş/çıkış gibi gün bazlı alanlar. */
export type IsoDate = string

/** ISO 8601 tarih-saat / instant (YYYY-MM-DDTHH:mm:ssZ) — uçuş kalkış/varış gibi an bazlı alanlar. */
export type IsoDateTime = string

/** ISO 4217 para birimi kodu (ör. "EUR", "TRY", "USD"). */
export type CurrencyCode = string

/**
 * Para tutarı. Fiyat asla salt sayı olarak taşınmaz; birimiyle birlikte gelir
 * (TourVisio tutarı + para birimi döndürür — CLAUDE.md: fiyat uydurma yok).
 */
export interface Money {
  amount: number
  currency: CurrencyCode
}
