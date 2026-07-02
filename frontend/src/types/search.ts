import type { IsoDate, CurrencyCode, CountryCode } from './common'

/**
 * Arama kriterleri — KALICI DEĞİL. chat_sessions.accumulated_criteria (jsonb) içinde
 * geçici tutulur; V1 şeması alan adlarını sabitlemez. Aşağıdaki alanlar kalıcı snapshot
 * sütunları (hotel/flight_reservation_details) ve backend record'larıyla hizalıdır.
 * Chat slot-filling'de `Partial<...>` kullanılır (bkz. chat.ts `PartialCriteria`).
 */

/** Uçuş yön tipi. DB: flight_reservation_details.trip_type CHECK ('one_way','round_trip'). */
export type TripType = 'one_way' | 'round_trip'

/** Fiyat aralığı filtresi (frontend-only). */
export interface PriceRange {
  min?: number
  max?: number
}

/** "HH:mm" (24s) zaman aralığı — kalkış saatini daraltmak için (frontend-only). */
export interface TimeRange {
  from?: string
  to?: string
}

/** Otel arama kriteri. `destination` = backend HotelSearchCriteria record'undaki alan. */
export interface HotelSearchCriteria {
  destination: string
  hotelName?: string
  checkIn: IsoDate // DB: check_in date
  checkOut: IsoDate // DB: check_out date
  adults: number // DB: adults smallint (>=1)
  children: number // DB: children smallint (>=0)
  /** Frontend-only: yaşa bağlı fiyatlama; DB'de saklanmaz. Uzunluğu `children` ile tutarlı. */
  childAges: number[]
  rooms: number // DB: rooms smallint (>=1)
  nationality: CountryCode
  currency: CurrencyCode
  // — frontend filtreleri (DB'de karşılığı yok) —
  stars?: number[]
  /** DB board_type serbest metin; UI filtresinde bilinen kodlar (RO/BB/HB/FB/AI/UAI). */
  boardType?: string
  priceRange?: PriceRange
  region?: string
  sort?: 'price-asc' | 'price-desc' | 'stars-desc'
}

/** Uçuş arama kriteri. origin/destination + passengers(count) DB alanlarıyla hizalı. */
export interface FlightSearchCriteria {
  origin: string // DB: origin varchar(100)
  destination: string // DB: destination varchar(100)
  /** Kullanıcının seçtiği gün; kalıcı biçimde depart_time (instant) olarak saklanır. */
  departDate: IsoDate
  /** Yolcu SAYISI. DB: passenger_count smallint (>=1). */
  passengers: number
  currency: CurrencyCode
  tripType: TripType
  /** `tripType === 'round_trip'` iken. DB: return_depart_time. */
  returnDate?: IsoDate
  /** frontend filtre; DB `stops` ile eşlenir (nonstop → stops = 0). */
  nonstop?: boolean
  airline?: string
  departTimeRange?: TimeRange
  /** frontend filtre; DB baggage serbest metin. */
  baggage?: string
}
