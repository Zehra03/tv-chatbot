import type { ProductType, IsoDateTime, Money } from './common'

/**
 * Rezervasyon domaini (docs/frontend-architecture.md §7, §9). Kontrollü rezervasyon
 * akışında oluşturulur; chatbot asla booking yapmaz — kullanıcı formda açıkça onaylar.
 */

/** Rezervasyon durumu. */
export type ReservationStatus = 'pending' | 'confirmed' | 'cancelled' | 'failed'

/** Yolcu / misafir bilgisi. İletişim alanları formda Zod ile doğrulanır. */
export interface Passenger {
  firstName: string
  lastName: string
  email: string
  phone: string
}

export interface Reservation {
  id: string
  /** İnsan-okur rezervasyon numarası (ör. "PA-2026-00042"). */
  reservationNumber: string
  productType: ProductType
  /** Rezervasyonun oluşturulduğu an. */
  createdAt: IsoDateTime
  /** Ürünün kısa özeti (otel adı / uçuş rotası) — liste ve detayda gösterilir. */
  productSummary: string
  passengers: Passenger[]
  total: Money
  status: ReservationStatus
}
