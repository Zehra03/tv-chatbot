import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AuthUser } from '@/api'
import { setAuthToken, setGuestId, setRefreshToken } from '@/api/client'

/**
 * Gerçek oturum state'i — LoginPage authApi ile backend'e gider, dönen
 * { user, token, refreshToken } buraya yazılır (docs/frontend-architecture.md §5).
 * Jetonlar OPAK'tır; localStorage'da saklanır ki sayfa yenilemede oturum düşmesin.
 * Kısa ömürlü access jetonu 401 olunca client.ts interceptor'ı refresh jetonuyla
 * sessizce yeniler ve tokensRefreshed ile yeni çifti buraya yazar. Açılışta
 * SessionManager (providers.tsx) saklı jetonu GET /auth/me ile doğrular.
 */
export interface SessionUser extends AuthUser {
  /** "Misafir olarak devam et" ile giren kullanıcı — jetonu yoktur. */
  guest?: boolean
}

interface AuthState {
  user: SessionUser | null
  /** Backend access JWT'si; misafir oturumunda null. setAuthToken ile Axios'a aynalanır. */
  token: string | null
  /** Uzun ömürlü refresh jetonu; misafir oturumunda null. setRefreshToken ile aynalanır. */
  refreshToken: string | null
  /**
   * Misafir kimliği (opak X-Guest-Id); yalnızca misafir oturumunda dolu. Tarayıcıda bir kez
   * üretilip localStorage'da saklanır ki misafir sohbetleri sayfa yenilemede/dönüşte kalıcı olsun.
   * JWT değildir — düşük değerli misafir verisi için taşıyıcı anahtar. setGuestId ile Axios'a aynalanır.
   */
  guestId: string | null
}

const STORAGE_KEY = 'pax-auth'

/**
 * Misafir kimliği üretir. crypto.randomUUID (122-bit) tahmin edilemez; başka bir misafirin
 * kimliğini bilmeden onun oturumları okunamaz. Eski test ortamları için yedek yol bırakılır.
 * Backend guest_token sütunu 64 karakter; her iki biçim de bunun altında kalır.
 */
function generateGuestId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch {
    /* yedek yola geç */
  }
  return `guest-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

function loadStoredSession(): AuthState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthState
    return parsed?.user
      ? {
          user: parsed.user,
          token: parsed.token ?? null,
          refreshToken: parsed.refreshToken ?? null,
          guestId: parsed.guestId ?? null,
        }
      : null
  } catch {
    return null
  }
}

/** Storage kapalıysa (ör. Safari private) oturum yalnızca bellekte yaşar. */
function persistSession(state: AuthState) {
  try {
    if (state.user) {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          user: state.user,
          token: state.token,
          refreshToken: state.refreshToken,
          guestId: state.guestId,
        }),
      )
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    /* sessizce geç — kalıcılık iyileştirmedir, gereklilik değil */
  }
}

const stored = loadStoredSession()
// Jeton/misafir kimliğini modül yüklenirken Axios'a tanıt — ilk render'daki sorgular da
// doğru başlıkla gitsin ve interceptor gerekirse hemen refresh yapabilsin.
setAuthToken(stored?.token ?? null)
setRefreshToken(stored?.refreshToken ?? null)
setGuestId(stored?.guestId ?? null)

const initialState: AuthState = stored ?? {
  user: null,
  token: null,
  refreshToken: null,
  guestId: null,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    // persistSession/setAuthToken reducer içinde yan etkidir; bilinçli tercih:
    // kalıcılık tek yerde kalır ve testlerin kurduğu store'larda da aynı işler.
    sessionStarted(
      state,
      action: PayloadAction<{ user: AuthUser; token: string; refreshToken: string }>,
    ) {
      state.user = action.payload.user
      state.token = action.payload.token
      state.refreshToken = action.payload.refreshToken
      // Giriş yapan kullanıcı JWT ile gider; artık misafir kimliği taşımaz.
      state.guestId = null
      setAuthToken(state.token)
      setRefreshToken(state.refreshToken)
      setGuestId(null)
      persistSession(state)
    },
    /** client.ts sessiz refresh'i başarınca yeni jeton çiftini yazar. */
    tokensRefreshed(state, action: PayloadAction<{ token: string; refreshToken: string }>) {
      if (!state.user || state.user.guest) return
      state.token = action.payload.token
      state.refreshToken = action.payload.refreshToken
      setAuthToken(state.token)
      setRefreshToken(state.refreshToken)
      persistSession(state)
    },
    // guestId'yi prepare'de üretiriz: dispatch anında (reducer değil) yan etki için doğru yer.
    // Yalnızca "Misafir devam et" tıklamasında çağrılır; yenilemede saklı kimlik korunur.
    guestSessionStarted: {
      reducer(state, action: PayloadAction<{ guestId: string }>) {
        state.user = { id: 'guest', email: '', name: 'Misafir', guest: true }
        state.token = null
        state.refreshToken = null
        state.guestId = action.payload.guestId
        setAuthToken(null)
        setRefreshToken(null)
        setGuestId(action.payload.guestId)
        persistSession(state)
      },
      prepare() {
        return { payload: { guestId: generateGuestId() } }
      },
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
      state.refreshToken = null
      state.guestId = null
      setAuthToken(null)
      setRefreshToken(null)
      setGuestId(null)
      persistSession(state)
    },
  },
})

export const { sessionStarted, tokensRefreshed, guestSessionStarted, userRefreshed, logout } =
  authSlice.actions
export default authSlice.reducer
