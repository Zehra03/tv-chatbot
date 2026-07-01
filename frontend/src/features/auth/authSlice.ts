import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

/**
 * Mock kullanıcı — gerçek kimlik doğrulama backend'de. Frontend sadece oturumu
 * Redux'ta tutar (docs/frontend-architecture.md §5). LoginPage bağlanması Epic 3'te.
 */
export interface MockUser {
  email: string
  name?: string
  /** "Misafir olarak devam et" ile giren kullanıcı. */
  guest?: boolean
}

interface AuthState {
  user: MockUser | null
}

const initialState: AuthState = {
  user: null,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    login(state, action: PayloadAction<MockUser>) {
      state.user = action.payload
    },
    logout(state) {
      state.user = null
    },
  },
})

export const { login, logout } = authSlice.actions
export default authSlice.reducer
