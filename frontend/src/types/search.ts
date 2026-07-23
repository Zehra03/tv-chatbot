import type { IsoDate, CurrencyCode, CountryCode } from './common'

/**
 * Arama kriterleri — KALICI DEĞİL. chat_sessions.accumulated_criteria (jsonb) içinde
 * geçici tutulur; V1 şeması alan adlarını sabitlemez. Aşağıdaki alanlar kalıcı snapshot
 * sütunları (hotel/flight_reservation_details) ve backend record'larıyla hizalıdır.
 * Chat slot-filling'de `Partial<...>` kullanılır (bkz. chat.ts `PartialCriteria`).
 */

/**
 * Tek aramada izin verilen azami misafir/yolcu sayısı (otelde yetişkin, uçuşta yolcu).
 * Backend bir üst sınır dayatmaz (adults yalnız >=1); bu sırf bir UI/UX limitidir:
 * seyahat motorları grup boyutunu tek aramada ~9 ile sınırlar, ötesi TourVisio
 * aramasını anlamsızlaştırır ve büyük gruplar tipik olarak ayrı bir akışa yönlendirilir.
 */
export const MAX_PARTY_SIZE = 9

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

/**
 * Uçuş kalkış/varış otomatik tamamlama önerisi (backend `FlightLocationDto`).
 * Serbest metin ("Ant") → gerçek TourVisio konumu. `id` arama origin/destination
 * olarak geri gönderilir; `name` dropdown'da gösterilir; `code` (IATA) yalnız havalimanında.
 */
export interface FlightLocation {
  id: string
  code?: string | null
  name: string
  type: 'city' | 'airport'
}

/**
 * Otel destination otomatik tamamlama önerisi (backend `HotelLocationDto`). Uçuştan
 * farkı: `code` yok ve seçilince aramaya `name` gider (otel araması ismi tekrar
 * TourVisio konum id'sine çözer), `id` değil.
 */
export interface HotelLocation {
  id: string
  name: string
  type: 'city'
}

/** Otel arama kriteri. `destination` = backend HotelSearchCriteria record'undaki alan. */
export interface HotelSearchCriteria {
  destination: string
  hotelName?: string
  checkIn: IsoDate // DB: check_in date
  checkOut: IsoDate // DB: check_out date
  /**
   * Konaklama süresi (gece). Chat niyet çıkarımı konaklamayı checkOut yerine gün sayısıyla
   * yakalayabilir ("5 gece") — o durumda `checkOut` boş gelir ve buildHotelDraft'ta checkIn+nights'tan
   * türetilir (backend `HotelCriteriaMapper.computeNights` ile simetrik). Formda kullanılmaz (orada
   * checkOut hep seçilir); yalnız accumulatedCriteria yolunda yaşar.
   */
  nights?: number
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

/**
 * Uçuşta refakatteki bebeğin (kucak bebeği) en büyük yaşı — backend
 * `PassengerCount.ofChildAges`teki `INFANT_MAX_AGE` ile birebir aynı tutulmalı.
 */
export const FLIGHT_INFANT_MAX_AGE = 1

/**
 * Uçuşta bir çocuğun en büyük yaşı — bu sınırın üstü yetişkin ücretiyle uçar.
 * Otelden farklı olarak 0'dan başlar: 0–{@link FLIGHT_INFANT_MAX_AGE} INFANT (kucak
 * bebeği, backend `PassengerCount.ofChildAges`), {@link FLIGHT_INFANT_MAX_AGE}+1–
 * {@link FLIGHT_CHILD_MAX_AGE} CHILD.
 */
export const FLIGHT_CHILD_MAX_AGE = 17

/**
 * Uçuş arama kriteri. origin/destination DB alanlarıyla hizalı; yolcu sayısı
 * (backend `passenger_count`) `adults + childAges.length`'ten türetilir.
 */
export interface FlightSearchCriteria {
  origin: string // DB: origin varchar(100)
  destination: string // DB: destination varchar(100)
  /** Kullanıcının seçtiği gün; kalıcı biçimde depart_time (instant) olarak saklanır. */
  departDate: IsoDate
  /** Yetişkin yolcu SAYISI (>=1). */
  adults: number
  /**
   * Refakatteki her çocuğun yaşı; backend `PassengerCount.ofChildAges` bunu ücret tipine
   * (infant/child/adult ücreti) çevirir — otelin `childAges`'i ile aynı fikir, TourVisio'nun
   * uçuş ücret bantlarına göre. Boş dizi = refakatte çocuk yok.
   */
  childAges: number[]
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
