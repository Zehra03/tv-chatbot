import type {
  FlightProduct,
  FlightSearchCriteria,
  HotelProduct,
  HotelSearchCriteria,
  TripType,
} from '@/types'
import { formatDateTime } from '@/utils/format'
import type { FlightReservationDraft, HotelReservationDraft } from './reservationDraftSlice'

/**
 * Seçilen ürün + arama kriterinden rezervasyon taslağı kurar. Otel snapshot'ının backend'in @NotNull
 * istediği alanları (check-in/out, oda/kişi, uyruk) ÜRÜNDE değil KRİTERDE yaşar — bu yüzden "Seç"
 * anında kriterden doldurulur (docs/frontend-architecture.md §9). Uçuşta ise yolcu sayısı dışında her
 * şey üründedir. Fiyat üründen alınır (uydurulmaz); backend `totalAmount`'ı bu snapshot'tan doğrular.
 */

/**
 * Otel taslağı; tahmin edilemeyen zorunlu alanlar (giriş/çıkış tarihi + yetişkin sayısı) kriterde
 * eksikse `null` → "Seç" kapatılır. `rooms` eksikse 1'e düşülür (backend: her oda ≥1 yetişkin, yani
 * rooms ≤ adults; 1 daima güvenli) — chat kriterinde oda sayısı taşınmayabilir.
 */
export function buildHotelDraft(
  product: HotelProduct,
  criteria: Partial<HotelSearchCriteria> | undefined,
): HotelReservationDraft | null {
  if (!criteria?.checkIn || !criteria.checkOut || !criteria.adults) {
    return null
  }
  return {
    productType: 'hotel',
    offerId: product.offerId,
    title: product.hotelName,
    summary: `${product.region} · ${product.stars}★ · ${product.boardType}`,
    price: product.price,
    currency: product.currency,
    // Çocuk yaşları form ön-doldurması için; uzunluk children ile hizalanır (fazlası kırpılır).
    childAges: (criteria.childAges ?? []).slice(0, criteria.children ?? 0),
    hotel: {
      hotelName: product.hotelName,
      region: product.region,
      stars: product.stars,
      boardType: product.boardType,
      checkIn: criteria.checkIn,
      checkOut: criteria.checkOut,
      rooms: criteria.rooms ?? 1,
      adults: criteria.adults,
      children: criteria.children ?? 0,
      nationality: criteria.nationality ?? null,
      price: product.price,
      currency: product.currency,
    },
  }
}

/** Uçuş taslağı; tüm snapshot alanları üründe var, yalnız yolcu sayısı kriterden (yoksa 1). */
export function buildFlightDraft(
  product: FlightProduct,
  criteria: Partial<FlightSearchCriteria> | undefined,
): FlightReservationDraft {
  // tripType backend'de @NotNull. Chat sonuç kartının uçuş DTO'su bu alanı taşımayabildiğinden
  // önce arama kriterinden (formda hep seçili), sonra üründen, en son dönüş bacağından türetilir.
  const tripType: TripType =
    criteria?.tripType ?? product.tripType ?? (product.returnDepartTime ? 'round_trip' : 'one_way')
  return {
    productType: 'flight',
    offerId: product.id,
    title: `${product.airline} ${product.origin} → ${product.destination}`,
    summary: `${formatDateTime(product.departTime)} · ${
      product.stops === 0 ? 'Direkt' : `${product.stops} aktarma`
    } · Bagaj: ${product.baggage}`,
    price: product.price,
    currency: product.currency,
    flight: {
      origin: product.origin,
      destination: product.destination,
      airline: product.airline,
      tripType,
      departTime: product.departTime,
      arriveTime: product.arriveTime,
      returnDepartTime: product.returnDepartTime ?? null,
      returnArriveTime: product.returnArriveTime ?? null,
      stops: product.stops,
      baggage: product.baggage,
      passengerCount: criteria?.passengers ?? 1,
      price: product.price,
      currency: product.currency,
    },
  }
}
