import { configureStore } from '@reduxjs/toolkit'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'

/**
 * Redux Toolkit store — yalnızca istemci/UI state'i (auth, chat, rezervasyon
 * taslağı, ui). Sunucu verisi React Query'e ait (docs/frontend-architecture.md §5).
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
    chat: chatReducer,
    reservationDraft: reservationDraftReducer,
    ui: uiReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
