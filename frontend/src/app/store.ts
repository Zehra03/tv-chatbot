import { configureStore } from '@reduxjs/toolkit'
import authReducer from '@/features/auth/authSlice'

/**
 * Redux Toolkit store — yalnızca istemci/UI state'i (auth, chat, rezervasyon
 * taslağı, ui). Sunucu verisi React Query'e ait (docs/frontend-architecture.md §5).
 * Yeni slice'lar geldikçe (chatSlice, reservationDraftSlice, uiSlice) buraya eklenir.
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
