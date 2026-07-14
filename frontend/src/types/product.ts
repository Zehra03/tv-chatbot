import type { CurrencyCode, IsoDateTime } from './common'
import type { TripType } from './search'

/**
 * Arama sonucu ürünleri — KALICI DEĞİL. TourVisio'dan gelen geçici sonuçlar
 * (V1 şema notu: yalnızca rezerve edilen snapshot saklanır). Fiyat düz `price + currency`.
 *
 * `HotelProduct` backend `hotel/HotelProduct.java` record'unun wire şekliyle BİREBİR.
 * `FlightProduct` için backend record'u HENÜZ YOK → flight_reservation_details
 * sütunlarından türetildi (provisional; backend record'u gelince sabitlenecek).
 */

export interface HotelProduct {
  id: string
  /**
   * TourVisio teklif jetonu — rezervasyonu başlatan BeginTransaction API'sinin istediği opak token
   * (backend `hotel/HotelProduct.java` `offerId`). `id` (otel kimliği) ile KARIŞTIRILMAMALI:
   * booking `offerId` ile yapılır. Uçuşta ayrı alan yoktur — orada `FlightProduct.id` zaten offer jetonu.
   */
  offerId: string
  hotelName: string
  region: string
  /** 1–5. DB: stars smallint. */
  stars: number
  price: number
  currency: CurrencyCode
  /** DB: board_type varchar(50) — serbest metin (ör. "BB", "AI"). */
  boardType: string
  availability: boolean
  /**
   * Otel görseli — TourVisio pricesearch `thumbnailFull` (mutlak URL). Sağlayıcıda
   * görsel yoksa null/undefined gelir; kart placeholder gösterir. Asla uydurulmaz.
   */
  image?: string | null
}

export interface FlightProduct {
  id: string
  airline: string
  origin: string
  destination: string
  /** origin/destination havalimanı kodunun ait olduğu şehir (ör. SAW → "Istanbul"), TourVisio'dan. */
  originCity?: string | null
  destinationCity?: string | null
  departTime: IsoDateTime
  arriveTime: IsoDateTime | null
  tripType: TripType
  /** round_trip iken dolu; one_way'de null. */
  returnDepartTime?: IsoDateTime | null
  returnArriveTime?: IsoDateTime | null
  /** Aktarma sayısı (0 = direkt). DB: stops smallint. */
  stops: number
  /** DB: baggage varchar(50) — serbest metin (ör. "20kg"), boolean DEĞİL. */
  baggage: string
  price: number
  currency: CurrencyCode
}
