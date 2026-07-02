import type { IsoDateTime, Money } from './common'
import type { BoardType } from './search'

/**
 * Arama sonucu ürünleri (docs/frontend-architecture.md §7). Backend/MSW'den gelir;
 * fiyat/müsaitlik yalnızca sunucudan doldurulur — frontend değer uydurmaz (CLAUDE.md).
 */

export interface HotelProduct {
  id: string
  name: string
  region: string
  /** Yıldız sayısı (1–5). */
  stars: number
  boardType: BoardType
  price: Money
  /** Seçilen kriter için rezerve edilebilir mi. */
  available: boolean
}

export interface FlightProduct {
  id: string
  airline: string
  departTime: IsoDateTime
  arriveTime: IsoDateTime
  /** Aktarma sayısı (0 = direkt). */
  stops: number
  /** Fiyata bagaj dahil mi. */
  baggageIncluded: boolean
  price: Money
}
