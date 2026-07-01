import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export type ProductType = 'hotel' | 'flight'

/**
 * Rezervasyon taslağı — chat/otel/uçuş sonuçlarından "Seç" ile seçilen ürünün,
 * rezervasyon formunu doldurmaya yetecek NORMALİZE edilmiş özeti. Ürün-tipi
 * bağımsız tutulur ki hem otel hem uçuş aynı forma akabilsin
 * (docs/frontend-architecture.md §9). Sayfalar arası devir sözleşmesidir.
 */
export interface ReservationDraft {
  productType: ProductType
  productId: string
  title: string
  summary: string
  price: number
  currency: string
}

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
})

export const { setDraft, clearDraft } = reservationDraftSlice.actions
export default reservationDraftSlice.reducer
