import type { IsoDate, IsoDateTime, CurrencyCode, CountryCode } from './common'
import type { TripType } from './search'

/**
 * Rezervasyon domaini — KALICI. Backend sözleşmesiyle (reservation/web/dto/*) BİREBİR hizalı.
 * Kontrollü rezervasyon akışında oluşturulur; chatbot asla booking yapmaz.
 *
 * Enum'lar backend'de küçük harf/snake_case serileşir (ProductType/ReservationStatus/PassengerType/
 * TripType üzerindeki @JsonValue), bu yüzden buradaki birlik tipleri de küçük harftir.
 * `id` DB'de bigint → JSON'da number.
 */

/** DB: reservations.status CHECK ('pending','confirmed','cancelled','failed'). */
export type ReservationStatus = 'pending' | 'confirmed' | 'cancelled' | 'failed'

/** DB: passengers.passenger_type CHECK ('adult','child'). */
export type PassengerType = 'adult' | 'child'

/**
 * Rezervasyon ürün tipi. DB: reservations.product_type CHECK ('hotel','flight','combined').
 * Arama/kart tarafındaki `ProductType` ('hotel'|'flight')'ten ayrıdır: `combined` yalnız paket
 * rezervasyonlarda (hem otel hem uçuş) görülür.
 */
export type ReservationProductType = 'hotel' | 'flight' | 'combined'

/** Yolcu / misafir. DB: `passengers` tablosu. İletişim + age + nationality nullable (PII — loglanmaz). */
export interface Passenger {
  firstName: string // first_name varchar(100) NOT NULL
  lastName: string // last_name varchar(100) NOT NULL
  passengerType: PassengerType
  age?: number | null // age integer nullable (0–120)
  nationality?: CountryCode | null // nationality varchar(2) nullable
  email?: string | null // email varchar(254) nullable
  phone?: string | null // phone varchar(32) nullable
}

/**
 * Liste satırı / oluşturulan rezervasyon görünümü — yalnız başlık alanları
 * (backend `ReservationSummaryResponse`). Yolcu/ürün detayı içermez.
 */
export interface ReservationSummary {
  id: number // reservations.id bigint
  reservationNumber: string // reservation_number varchar(32) UNIQUE (iç kod "PAX-yyyyMMdd-XXXXXX")
  /** TourVisio referansı (ör. "RC002576"); satın alınana dek null. */
  externalReservationNumber?: string | null
  status: ReservationStatus
  productType: ReservationProductType
  reservationDate: IsoDate // reservation_date date
  totalAmount: number // total_amount numeric(12,2)
  currency: CurrencyCode // currency char(3)
  /** Liste ekranı için denormalize misafir adı. DB: lead_guest_name varchar(200), nullable. */
  leadGuestName?: string | null
}

/** Rezerve edilmiş otel snapshot'ı (backend `ReservationDetailResponse.Hotel`). */
export interface HotelDetail {
  hotelName: string
  region?: string | null
  stars?: number | null
  boardType?: string | null
  checkIn: IsoDate
  checkOut: IsoDate
  rooms: number
  adults: number
  children?: number | null
  nationality?: CountryCode | null
  price: number
  currency: CurrencyCode
}

/** Rezerve edilmiş uçuş snapshot'ı (backend `ReservationDetailResponse.Flight`). */
export interface FlightDetail {
  origin: string
  destination: string
  airline?: string | null
  tripType: TripType
  departTime: IsoDateTime
  arriveTime?: IsoDateTime | null
  returnDepartTime?: IsoDateTime | null
  returnArriveTime?: IsoDateTime | null
  stops?: number | null
  baggage?: string | null
  passengerCount: number
  price: number
  currency: CurrencyCode
}

/** Para tutarı + birim (backend `ReservationDetailResponse.Money`). */
export interface Money {
  amount: number
  currency: CurrencyCode
}

/** Servis bazında iptal cezası kırılımı (backend `PriceDetail`). */
export interface CancellationPriceDetail {
  totalSalePrice?: number | null
  penalty?: number | null
  mainServiceFee?: number | null
}

/** İptal edilebilecek tek servis (backend `CancellationServiceOption`). */
export interface CancellationServiceOption {
  id: string
  code?: string | null
  name?: string | null
  productType?: number | null
  cancelable?: boolean | null
  price?: Money | null
  priceDetail?: CancellationPriceDetail | null
}

/**
 * Kullanıcının seçebileceği bir iptal sebebi + toplam ceza ve servis kırılımı
 * (backend `CancellationOption`). `reasonId`, iptalde `CancelRequest.reason` olarak geri gönderilir.
 */
export interface CancellationOption {
  reasonId: string
  reasonName?: string | null
  reasonComment?: string | null
  cancelable?: boolean | null
  price?: Money | null
  services?: CancellationServiceOption[] | null
}

/**
 * Tek rezervasyonun tam dökümü (backend `ReservationDetailResponse`): başlık alanları +
 * yolcular + otel/uçuş snapshot'ı + canlı TourVisio iptal seçenekleri. `hotel`/`flight` yoksa null;
 * `cancellationOptions` dış referans yoksa/lookup başarısızsa boş.
 */
export interface ReservationDetail extends ReservationSummary {
  passengers?: Passenger[]
  hotel?: HotelDetail | null
  flight?: FlightDetail | null
  cancellationOptions?: CancellationOption[]
}
