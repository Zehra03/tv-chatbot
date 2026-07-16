import { createSlice, isAnyOf, type PayloadAction } from '@reduxjs/toolkit'
import type { FlightSnapshotInput, HotelSnapshotInput } from '@/api'
import type { ProductType } from '@/types'
import { guestSessionStarted, logout, sessionStarted } from '@/features/auth/authSlice'

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
  /** Kimlik sınırında taslağı düşür: bir hesabın seçtiği ürün, çıkış/misafir/giriş
   * sonrası bir sonraki kimliğe taşınmasın (chatSlice ile aynı gerekçe). */
  extraReducers: (builder) => {
    builder.addMatcher(isAnyOf(logout, sessionStarted, guestSessionStarted), () => initialState)
  },
})

export const { setDraft, clearDraft } = reservationDraftSlice.actions
export default reservationDraftSlice.reducer
