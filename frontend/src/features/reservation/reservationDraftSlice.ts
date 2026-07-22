import { createSlice, isAnyOf, type PayloadAction } from '@reduxjs/toolkit'
import type { FlightSnapshotInput, HotelSnapshotInput } from '@/api'
import type { ProductType } from '@/types'
import { guestSessionStarted, logout } from '@/features/auth/authSlice'

export type { ProductType }

/**
 * Rezervasyon taslağı — chat/otel/uçuş sonuçlarından "Seç" ile seçilen ürünün rezervasyonu
 * başlatmaya yetecek TAM snapshot'ı. Backend `PreviewReservationCommand` `hotel`/`flight` bloklarını
 * (@NotNull check-in/out, oda/kişi vb.) doğrudan taşır ki form yalnız yolcu bilgisini eklesin.
 * Bu alanlar üründe DEĞİL arama kriterinde yaşadığından "Seç" anında kriterden doldurulur
 * (docs/frontend-architecture.md §9). Sayfalar arası devir sözleşmesidir.
 *
 * `offerId` = TourVisio teklif jetonu (otel: HotelProduct.offerId; uçuş: FlightProduct.offerId —
 * arama-satırı id'si DEĞİL; `id` ile booking "offer no longer bookable" verir, bkz. buildFlightDraft).
 * `title`/`summary`/`price`/`currency` yalnız ekran gösterimi içindir.
 */
export interface HotelReservationDraft {
  productType: 'hotel'
  offerId: string
  title: string
  summary: string
  price: number
  currency: string
  hotel: HotelSnapshotInput
  /** Çocuk yaşları (aramadan) — form yolcu satırlarını doğru sayı/tip/yaşla önden doldurmak için.
   * Uzunluğu `hotel.children` ile tutarlıdır; backend snapshot'ında saklanmaz (yalnız yolcu satırı). */
  childAges: number[]
}

export interface FlightReservationDraft {
  productType: 'flight'
  offerId: string
  /** Gidiş-dönüşte dönüş bacağının jetonu; tek yönde null. Rezervasyon ikisini birden gönderir. */
  returnOfferId?: string | null
  title: string
  summary: string
  price: number
  currency: string
  flight: FlightSnapshotInput
  /** Çocuk/bebek yaşları (aramadan) — HATA 5 fix: form yolcu satırlarını doğru sayı/tip/yaşla
   * önden doldurmak için (bkz. HotelReservationDraft.childAges). Uzunluğu
   * `flight.passengerCount - adults` ile tutarlıdır; backend snapshot'ında saklanmaz. */
  childAges: number[]
}

export type ReservationDraft = HotelReservationDraft | FlightReservationDraft

interface ReservationDraftState {
  draft: ReservationDraft | null
}

const initialState: ReservationDraftState = { draft: null }

const reservationDraftSlice = createSlice({
  name: 'reservationDraft',
  initialState,
  reducers: {
    setDraft(state, action: PayloadAction<ReservationDraft>) {
      state.draft = action.payload
    },
    clearDraft(state) {
      state.draft = null
    },
  },
  /**
   * Kimlik sınırında taslağı düşür: bir hesabın seçtiği ürün bir sonraki kimliğe taşınmasın.
   *
   * `sessionStarted` bilinçli olarak DIŞARIDA: misafirin hesaba yükselmesi kimlik sınırı değil,
   * aynı kişinin devamıdır. Misafir "Seç"e basınca RequireAccount onu /login'e `from:
   * '/reservation/new'` ile yolluyor ve giriş sonrası goToApp() tam oraya geri getiriyor —
   * `sessionStarted` burada taslağı silseydi akış kendini imha eder, kullanıcı döndüğü formda
   * "Önce bir ürün seçmelisiniz" görürdü.
   *
   * Hesap→hesap sızıntısı yine kapalı: oturumu açık gerçek bir kullanıcı /login'e giremiyor
   * (LoginPage real oturumu /chat'e bounce ediyor), yani B giriş yapmadan önce A'nın `logout`'u
   * zorunlu olarak geçiyor — ve `logout` taslağı siliyor. Süresi dolan oturum da 401 →
   * UNAUTHORIZED_EVENT → logout yolundan aynı yere düşüyor.
   *
   * chatSlice'tan bilerek AYRILIR (o `sessionStarted`'da da sıfırlar): sohbet thread'i sunucuda
   * kimliğe bağlı yaşıyor — misafirin oturumları X-Guest-Id ile sahipleniliyor ve giriş anında
   * guestId düştüğü için sunucu onları yeni kimliğe zaten servis etmiyor; thread'i tutmak
   * erişilemeyen veriyi ekranda bırakırdı. Taslak ise tamamen istemci-taraflı, kullanıcının az
   * önce seçtiği ürün — hiçbir kimliğe ait değil, taşınması doğru.
   */
  extraReducers: (builder) => {
    builder.addMatcher(isAnyOf(logout, guestSessionStarted), () => initialState)
  },
})

export const { setDraft, clearDraft } = reservationDraftSlice.actions
export default reservationDraftSlice.reducer
