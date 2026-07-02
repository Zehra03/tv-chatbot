import type { IsoDate, CurrencyCode } from './common'

/**
 * Arama kriterleri (docs/frontend-architecture.md §7). Chat slot-filling sırasında
 * kademeli dolduğundan, kısmi kriter için `Partial<HotelSearchCriteria>` /
 * `Partial<FlightSearchCriteria>` kullanılır (bkz. chat.ts `PartialCriteria`).
 */

/** Otel pansiyon tipi: RO=sadece oda, BB=kahvaltı, HB=yarım, FB=tam, AI=her şey dahil, UAI=ultra. */
export type BoardType = 'RO' | 'BB' | 'HB' | 'FB' | 'AI' | 'UAI'

/** Otel sonuç sıralaması. */
export type HotelSort = 'price-asc' | 'price-desc' | 'stars-desc'

/** Fiyat aralığı filtresi; uçları opsiyonel (tek taraflı aralık mümkün). */
export interface PriceRange {
  min?: number
  max?: number
}

/** "HH:mm" (24s) zaman aralığı — uçuş kalkış saatini daraltmak için. */
export interface TimeRange {
  from?: string
  to?: string
}

/** Otel arama kriterleri. `location` (şehir/bölge) veya `hotelName` ile arama yapılır. */
export interface HotelSearchCriteria {
  location?: string
  hotelName?: string
  checkIn: IsoDate
  checkOut: IsoDate
  adults: number
  children: number
  /** Çocuk yaşları; uzunluğu `children` ile tutarlı olmalı (yaşa bağlı fiyatlama). */
  childAges: number[]
  rooms: number
  nationality: string
  currency: CurrencyCode
  // — opsiyonel filtreler —
  stars?: number[]
  boardType?: BoardType
  priceRange?: PriceRange
  region?: string
  sort?: HotelSort
}

/** Uçuş yön tipi. */
export type TripType = 'one-way' | 'round-trip'

/** Uçuş yolcu dağılımı. */
export interface FlightPassengers {
  adults: number
  children: number
  infants: number
}

/** Uçuş arama kriterleri. `from`/`to` havalimanı ya da şehir kodu. */
export interface FlightSearchCriteria {
  from: string
  to: string
  departDate: IsoDate
  passengers: FlightPassengers
  currency: CurrencyCode
  tripType: TripType
  // — opsiyonel —
  /** `tripType === 'round-trip'` iken dolu. */
  returnDate?: IsoDate
  nonstop?: boolean
  airline?: string
  departTimeRange?: TimeRange
  baggageIncluded?: boolean
}
