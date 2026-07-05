import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AuthUser } from '@/api'
import { setAuthToken } from '@/api/client'

/**
 * Gerçek oturum state'i — LoginPage authApi ile backend'e gider, dönen
 * { user, token } buraya yazılır (docs/frontend-architecture.md §5). Jeton
 * OPAK'tır; localStorage'da saklanır ki sayfa yenilemede oturum düşmesin.
 * Açılışta SessionManager (providers.tsx) saklı jetonu GET /auth/me ile doğrular.
 */
export interface SessionUser extends AuthUser {
  /** "Misafir olarak devam et" ile giren kullanıcı — jetonu yoktur. */
  guest?: boolean
}

interface AuthState {
  user: SessionUser | null
  /** Backend JWT'si; misafir oturumunda null. setAuthToken ile Axios'a aynalanır. */
  token: string | null
}

const STORAGE_KEY = 'pax-auth'

function loadStoredSession(): AuthState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthState
    return parsed?.user ? { user: parsed.user, token: parsed.token ?? null } : null
  } catch {
    return null
  }
}

/** Storage kapalıysa (ör. Safari private) oturum yalnızca bellekte yaşar. */
function persistSession(state: AuthState) {
  try {
    if (state.user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ user: state.user, token: state.token }))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    /* sessizce geç — kalıcılık iyileştirmedir, gereklilik değil */
  }
}

const stored = loadStoredSession()
// Jetonu modül yüklenirken Axios'a tanıt — ilk render'daki sorgular da yetkili gitsin.
setAuthToken(stored?.token ?? null)

const initialState: AuthState = stored ?? { user: null, token: null }

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    // persistSession/setAuthToken reducer içinde yan etkidir; bilinçli tercih:
    // kalıcılık tek yerde kalır ve testlerin kurduğu store'larda da aynı işler.
    sessionStarted(state, action: PayloadAction<{ user: AuthUser; token: string }>) {
      state.user = action.payload.user
      state.token = action.payload.token
      setAuthToken(state.token)
      persistSession(state)
    },
    guestSessionStarted(state) {
      state.user = { id: 'guest', email: '', name: 'Misafir', guest: true }
      state.token = null
      setAuthToken(null)
      persistSession(state)
    },
    /** Açılıştaki GET /auth/me yanıtıyla saklı kullanıcı bilgisini tazeler. */
    userRefreshed(state, action: PayloadAction<AuthUser>) {
      if (!state.user || state.user.guest) return
      state.user = action.payload
      persistSession(state)
    },
    logout(state) {
      state.user = null
      state.token = null
      setAuthToken(null)
      persistSession(state)
    },
  },
})

export const { sessionStarted, guestSessionStarted, userRefreshed, logout } = authSlice.actions
export default authSlice.reducer
