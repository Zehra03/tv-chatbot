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
   * booking `offerId` ile yapılır. Uçuşta da ayrı bir `offerId` vardır (bkz. `FlightProduct.offerId`) —
   * uçuşun `id`'si düz UUID'dir, booking jetonu DEĞİLDİR.
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
  /**
   * TourVisio teklif jetonu (kodlanmış, opak) — BeginTransaction'ın istediği token. `id` (arama-satırı
   * UUID'si) ile KARIŞTIRILMAMALI: booking `offerId` ile yapılır. Backend `flight/FlightProductApiDto`
   * bu alanı zaten döner; `id` göndermek TourVisio'da `GeneralException` ("offer no longer bookable")
   * verir.
   */
  offerId: string
  /**
   * round_trip iken dolu: dönüş bacağının TourVisio teklif jetonu. TourVisio gidiş ve dönüşü ayrı
   * fiyatlar ve ayrı jetonlarla döner, bu yüzden gidiş-dönüş rezervasyonu İKİ jetonla yapılır —
   * tek başına `offerId` göndermek tek yön bilet alır.
   */
  returnOfferId?: string | null
  airline: string
  origin: string
  destination: string
  /** origin/destination havalimanı kodunun ait olduğu şehir (ör. SAW → "Istanbul"), TourVisio'dan. */
  originCity?: string | null
  destinationCity?: string | null
  departTime: IsoDateTime
  arriveTime: IsoDateTime | null
  /**
   * OPSİYONEL — tel üzerindeki gerçeğe uyar: `/flights/search` bunu doldurur
   * (FlightProductApiDto), ama chat sonuç kartları ürünü domain nesnesinden serileştirdiğinden
   * alanı TAŞIMAZ. Zorunlu tiplenmişken tsc "hep var" diyordu ve `product.tripType ===
   * 'round_trip'` yazan her yeni okuyucu chat kartlarında sessizce false alıyordu — gidiş-dönüş
   * tek yön gibi render oluyordu (bkz. FlightCard'ın `!!returnDepartTime` çözümü ve
   * buildFlightDraft'ın yedek zinciri). Okurken daima yedeğe düşün.
   */
  tripType?: TripType
  /** round_trip iken dolu; one_way'de null. */
  returnDepartTime?: IsoDateTime | null
  returnArriveTime?: IsoDateTime | null
  /** Dönüş bacağının havayolu ve aktarma sayısı; one_way'de null/0. */
  returnAirline?: string | null
  returnStops?: number
  /** Aktarma sayısı (0 = direkt). DB: stops smallint. */
  stops: number
  /** DB: baggage varchar(50) — serbest metin (ör. "20kg"), boolean DEĞİL. */
  baggage: string
  price: number
  currency: CurrencyCode
}
