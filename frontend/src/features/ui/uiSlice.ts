import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

/**
 * Geçici UI state'i (docs/frontend-architecture.md §5): sonuç listelerinin
 * aktif filtreleri, açık modal ve toast. Sunucu verisi React Query'de kalır —
 * filtreler sunucudan gelen listeye istemci tarafında uygulanır.
 */

export type HotelSort = 'price-asc' | 'price-desc' | 'stars-desc'
export type FlightSort = 'price-asc' | 'depart-asc'

export interface HotelFilters {
  /** En az yıldız (ör. 4 → 4★ ve üzeri). */
  minStars: number | null
  boardType: string | null
  maxPrice: number | null
  sort: HotelSort | null
}

export interface FlightFilters {
  nonstopOnly: boolean
  airline: string | null
  maxPrice: number | null
  sort: FlightSort | null
}

export interface UiState {
  hotelFilters: HotelFilters
  flightFilters: FlightFilters
  /** Açık modalın kimliği (ör. 'reservation-cancel'); kapalıyken null. */
  activeModal: string | null
  /** Kısa bilgi mesajı; gösterilecek bir şey yokken null. */
  toast: string | null
}

const emptyHotelFilters: HotelFilters = {
  minStars: null,
  boardType: null,
  maxPrice: null,
  sort: null,
}

const emptyFlightFilters: FlightFilters = {
  nonstopOnly: false,
  airline: null,
  maxPrice: null,
  sort: null,
}

const initialState: UiState = {
  hotelFilters: emptyHotelFilters,
  flightFilters: emptyFlightFilters,
  activeModal: null,
  toast: null,
}

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    hotelFiltersChanged(state, action: PayloadAction<Partial<HotelFilters>>) {
      Object.assign(state.hotelFilters, action.payload)
    },
    hotelFiltersReset(state) {
      state.hotelFilters = emptyHotelFilters
    },
    flightFiltersChanged(state, action: PayloadAction<Partial<FlightFilters>>) {
      Object.assign(state.flightFilters, action.payload)
    },
    flightFiltersReset(state) {
      state.flightFilters = emptyFlightFilters
    },
    modalOpened(state, action: PayloadAction<string>) {
      state.activeModal = action.payload
    },
    modalClosed(state) {
      state.activeModal = null
    },
    toastShown(state, action: PayloadAction<string>) {
      state.toast = action.payload
    },
    toastCleared(state) {
      state.toast = null
    },
  },
})

export const {
  hotelFiltersChanged,
  hotelFiltersReset,
  flightFiltersChanged,
  flightFiltersReset,
  modalOpened,
  modalClosed,
  toastShown,
  toastCleared,
} = uiSlice.actions
export default uiSlice.reducer
