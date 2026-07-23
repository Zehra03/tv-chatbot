import { apiClient } from './client'
import type {
  PassengerType,
  ReservationDetail,
  ReservationProductType,
  ReservationSummary,
  TripType,
} from '@/types'

/**
 * Rezervasyon endpoint'leri — backend `reservation/` modülüyle BİREBİR sözleşme
 * (docs/frontend-architecture.md §9). Stateful iki-adımlı akış:
 *   POST  /api/v1/reservations/preview     → snapshot'ı dondur, previewId al (yazma/TourVisio YOK)
 *   POST  /api/v1/reservations             → kesin onay (previewId | confirmationToken) → TourVisio
 *   GET   /api/v1/reservations             → liste (özet)
 *   GET   /api/v1/reservations/{id}        → detay (+ iptal seçenekleri)
 *   PATCH /api/v1/reservations/{id}/cancel → iptal (sebep + servisler)
 * Tüm endpoint'ler JWT ister (SecurityConfig: USER/ADMIN). Fiyatı backend hesaplamaz —
 * kullanıcının onayladığı `totalAmount` istemciden gelir (backend'de kabul edilen risk).
 * Kontrollü akış: kullanıcı formda açıkça onaylar; chatbot booking yapmaz.
 */

/** Bir yolcu — hem Passenger entity'sini hem TourVisio traveller'ını besleyen alanların alt kümesi. */
export interface TravellerInput {
  travellerId?: string
  firstName: string
  lastName: string
  passengerType: PassengerType
  /** TourVisio ünvan kodu (1 = Bay, 2 = Bayan). setReservationInfo için ZORUNLU. */
  title?: number
  /** Cinsiyet kodu (opsiyonel; backend null ise 0'a düşer). */
  gender?: number
  age?: number | null
  /** ISO-3166 alpha-2 uyruk. TourVisio için ZORUNLU. */
  nationalityCode?: string | null
  /** Doğum tarihi (yyyy-MM-dd). Uçuş biletlemesi için ZORUNLU (otelde opsiyonel). */
  birthDate?: string | null
  /** TC kimlik no (11 hane). Yurtiçi uçuş biletlemesi için ZORUNLU (otelde opsiyonel). */
  identityNumber?: string | null
  email?: string | null
  phone?: string | null
  /**
   * TourVisio'nun setReservationInfo'da okuduğu YAPISAL telefon. Backend mapper düz `phone` yerine
   * bunu (address.email ile birlikte) kullanır; uçuşta lider yolcuda ZORUNLU.
   */
  contactPhone?: { countryCode: string; areaCode: string; phoneNumber: string } | null
  /** Yolcu adresi — backend mapper e-postayı buradan (address.email) okur. */
  address?: { email?: string | null } | null
  /** İlk yolcu = lider (iletişim ona yazılır). */
  leader: boolean
}

/** Rezerve edilen otel snapshot'ı — backend `PreviewReservationCommand.Hotel` ile birebir. */
export interface HotelSnapshotInput {
  hotelName: string
  region?: string | null
  stars?: number | null
  boardType?: string | null
  checkIn: string // yyyy-MM-dd
  checkOut: string
  rooms: number
  adults: number
  children?: number | null
  nationality?: string | null
  price: number
  currency: string
}

/** Rezerve edilen uçuş snapshot'ı — backend `PreviewReservationCommand.Flight` ile birebir. */
export interface FlightSnapshotInput {
  origin: string
  destination: string
  airline?: string | null
  tripType: TripType
  departTime: string // OffsetDateTime ISO-8601
  arriveTime?: string | null
  returnDepartTime?: string | null
  returnArriveTime?: string | null
  stops?: number | null
  baggage?: string | null
  passengerCount: number
  price: number
  currency: string
}

/**
 * `/preview`'e gönderilen booking girdisi — backend `PreviewReservationCommand`. `userId` sunucuda
 * JWT'den enjekte edilir (GÖNDERİLMEZ). En az bir `hotel`/`flight` bulunmalı; ürün tipi hangilerinin
 * dolu olduğundan TÜRETİLİR (istemciden alınmaz).
 */
export interface PreviewReservationCommand {
  currency: string
  totalAmount: number
  culture?: string
  leadGuestName: string
  reservationNote?: string
  /** TourVisio teklif jetonları (BeginTransaction). Otel: HotelProduct.offerId; uçuş: FlightProduct.offerId. */
  offerIds: string[]
  travellers: TravellerInput[]
  hotel?: HotelSnapshotInput
  flight?: FlightSnapshotInput
}

/** `/preview` yanıtı — dondurulmuş özet + onaylanacak tutamaç (backend `PreviewResponse`). */
export interface PreviewResponse {
  previewId: string
  expiresAt: string // Instant ISO
  productType: ReservationProductType
  /** TourVisio'nun CANLI fiyatı — önizleme kurulurken yeniden okunur. */
  totalAmount: number
  currency: string
  leadGuestName: string
  passengerNames: string[]
  hasHotel: boolean
  hasFlight: boolean
  /**
   * Canlı fiyat aramada gösterilenden farklıysa true; `previousAmount` eski tutarı taşır
   * (fiyat oynamadıysa null). Backend bunları K21 için gönderiyor: ekran eski/yeni farkını
   * göstermeli ve onaydan ÖNCE ayrı bir açık kabul almalı. Tipte yoklardı, yani hiçbir şey
   * okumuyordu — kullanıcı yeniden fiyatlanmış bir rezervasyonu farkı hiç görmeden onaylıyordu.
   */
  priceChanged: boolean
  previousAmount?: number | null
  /** `previousAmount`'ın kendi para birimi — canlı `currency`'den farklı olabilir (ör. arama TRY
   * ile yapıldı, canlı yeniden fiyatlama EUR döndü). `previousAmount`'ı ASLA `currency` ile
   * biçimlendirmeyin, her zaman bunu kullanın. */
  previousCurrency?: string | null
  /** Önizleme yalnız TourVisio'nun fiyatlamayı kabul ettiği teklif için oluşur; sözleşmede
   * uygunluğu ima etmek yerine açıkça söyleyebilmek için var. */
  available: boolean
}

/** `/` (onay) gövdesi — normalde `previewId`; uyarı sonrası ikinci onayda `confirmationToken`. */
export interface ConfirmRequest {
  previewId?: string
  confirmationToken?: string
}

/** Onay bir uyarıyla durunca (ör. DuplicateReservationFound) 200 ile dönen gövde. */
export interface NeedsConfirmationResponse {
  confirmationToken: string
  warnings: string[]
}

/** Başarısız/ara sonuçlar için minimal gövde (backend `OutcomeResponse`). */
export interface OutcomeResponse {
  outcome: string
  message: string
}

/** İptal gövdesi — `reason` = detaydaki `cancellationOptions[].reasonId`. */
export interface CancelRequest {
  reason: string
  serviceIds?: string[]
}

/**
 * İptalin çok-durumlu sonucu — `ConfirmResult` ile aynı gerekçe. Axios her 2xx'i resolve eder,
 * ama 202 "iptal edildi" DEMEK DEĞİLDİR: TourVisio yanıtsız kaldığında backend
 * CANCEL_OUTCOME_UNKNOWN + 202 döner ve iptalin geçip geçmediğini bilmediğini söyler
 * (ReservationController: "the cancellation MAY have gone through — do not claim it failed").
 * Durum tipe taşınır ki çağıran 202'yi yanlışlıkla başarı sanmasın.
 */
export type CancelResult =
  | { kind: 'cancelled'; outcome: OutcomeResponse } // 200 — kesin iptal
  | { kind: 'pending'; outcome: OutcomeResponse } // 202 CANCEL_OUTCOME_UNKNOWN — sonuç belirsiz

/**
 * `/` (onay) çok-durumlu sonucu. Backend duruma göre farklı gövde + HTTP kodu döner; axios 2xx'i
 * resolve eder (201 oluştu / 200 uyarı / 202 belirsiz), 4xx-5xx reject → `ApiError` (mutation onError).
 */
export type ConfirmResult =
  | { kind: 'created'; reservation: ReservationSummary } // 201 + özet
  | { kind: 'createdFallback'; outcome: OutcomeResponse } // 201 "CONFIRMED" ama özet yeniden okunamadı (nadir)
  | { kind: 'needsConfirmation'; confirmationToken: string; warnings: string[] } // 200
  | { kind: 'pending'; outcome: OutcomeResponse } // 202 COMMIT_OUTCOME_UNKNOWN

export const reservationApi = {
  async preview(command: PreviewReservationCommand): Promise<PreviewResponse> {
    const res = await apiClient.post<PreviewResponse>('/api/v1/reservations/preview', command)
    return res.data
  },

  async confirm(body: ConfirmRequest): Promise<ConfirmResult> {
    // Varsayılan validateStatus (200-299) 200/201/202'yi resolve eder; 4xx-5xx reject olur.
    const res = await apiClient.post('/api/v1/reservations', body)
    const data = res.data as unknown
    if (res.status === 201) {
      if (data && typeof data === 'object' && 'reservationNumber' in data) {
        return { kind: 'created', reservation: data as ReservationSummary }
      }
      return { kind: 'createdFallback', outcome: data as OutcomeResponse }
    }
    if (res.status === 202) {
      return { kind: 'pending', outcome: data as OutcomeResponse }
    }
    const n = data as NeedsConfirmationResponse
    return { kind: 'needsConfirmation', confirmationToken: n.confirmationToken, warnings: n.warnings ?? [] }
  },

  async list(): Promise<ReservationSummary[]> {
    const res = await apiClient.get<ReservationSummary[]>('/api/v1/reservations')
    return res.data
  },

  async get(id: number): Promise<ReservationDetail> {
    const res = await apiClient.get<ReservationDetail>(`/api/v1/reservations/${id}`)
    return res.data
  },

  async cancel(id: number, body: CancelRequest): Promise<CancelResult> {
    // confirm() ile aynı okuma: durum kodu sonucun ANLAMINI taşır, gövde yalnız detayı.
    const res = await apiClient.patch<OutcomeResponse>(`/api/v1/reservations/${id}/cancel`, body)
    return res.status === 202
      ? { kind: 'pending', outcome: res.data }
      : { kind: 'cancelled', outcome: res.data }
  },
}
