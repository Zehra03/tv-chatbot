import type { ProductType, IsoDate, IsoDateTime, CurrencyCode, CountryCode } from './common'

/**
 * Rezervasyon domaini — KALICI. Kaynak: V1 şeması `reservations` (+ 1:N `passengers`).
 * Kontrollü rezervasyon akışında oluşturulur; chatbot asla booking yapmaz.
 * `id` DB'de bigint; JSON'a number/string olarak serileşmesi backend Jackson ayarına bağlı
 * (burada string alındı; netleşince güncellenebilir).
 */

/** DB: reservations.status CHECK ('pending','confirmed','cancelled','failed'). */
export type ReservationStatus = 'pending' | 'confirmed' | 'cancelled' | 'failed'

/** DB: passengers.passenger_type CHECK ('adult','child'). */
export type PassengerType = 'adult' | 'child'

/** Yolcu / misafir. DB: `passengers` tablosu. İletişim + age + nationality nullable (PII — loglanmaz). */
export interface Passenger {
  firstName: string // first_name varchar(100) NOT NULL
  lastName: string // last_name varchar(100) NOT NULL
  passengerType: PassengerType
  age?: number | null // age integer nullable (0–120)
  nationality?: CountryCode // nationality varchar(2) nullable
  email?: string // email varchar(254) nullable
  phone?: string // phone varchar(32) nullable
}

/** Rezervasyon başlığı. DB: `reservations` tablosu. */
export interface Reservation {
  id: string
  reservationNumber: string // reservation_number varchar(32) UNIQUE
  productType: ProductType
  status: ReservationStatus
  reservationDate: IsoDate // reservation_date date
  totalAmount: number // total_amount numeric(12,2)
  currency: CurrencyCode // currency char(3)
  /** Liste ekranı için denormalize misafir adı. DB: lead_guest_name varchar(200), nullable. */
  leadGuestName?: string
  createdAt: IsoDateTime
  updatedAt?: IsoDateTime
  /** 1:N; detay görünümünde toplanır (liste satırında dönmeyebilir). */
  passengers?: Passenger[]
}
