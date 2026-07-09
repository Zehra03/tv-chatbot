import axios, {
  type AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

/**
 * Tek Axios instance — tüm backend çağrıları buradan geçer.
 * - baseURL env'den gelir; MSW aktifken boş bırakılır → istekler aynı origin'e
 *   gider ve mock worker tarafından yakalanır.
 * - Request interceptor kısa ömürlü access jetonunu ekler.
 * - Response interceptor: (1) jetonlu bir istek 401 dönerse önce SESSİZCE refresh
 *   jetonuyla yeni access jetonu almayı dener ve isteği tekrarlar; refresh yoksa/
 *   başarısızsa oturumu düşürür. (2) Tüm hataları tek tip `ApiError`'a normalize eder.
 * - Burada HİÇBİR API anahtarı YOK; AI/TourVisio kimlik bilgileri backend'de (CLAUDE.md).
 */
export interface ApiError {
  status: number | null
  message: string
}

/**
 * Jetonlu bir istek 401 dönünce (ve sessiz refresh de başarısızsa) yayınlanan olay —
 * SessionManager (providers.tsx) dinler ve Redux oturumunu kapatır. Store'u buradan
 * import etmek döngü yaratırdı; olay köprüsü katmanları ayrık tutar.
 */
export const UNAUTHORIZED_EVENT = 'pax:unauthorized'

/**
 * Sessiz refresh jeton çiftini döndürünce yayınlanan olay — SessionManager yeni
 * { token, refreshToken } çiftini Redux'a + localStorage'a yazar. detail = TokenPair.
 */
export const TOKENS_REFRESHED_EVENT = 'pax:tokens-refreshed'

interface TokenPair {
  token: string
  refreshToken: string
}

/** Sessiz refresh sonrası ikinci kez denenmemesi için işaretlenen istek. */
interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

let authToken: string | null = null
let refreshTokenValue: string | null = null

/**
 * Access jetonunu istemciye tanıtır (null = oturum kapalı). authSlice çağırır;
 * jeton OPAK'tır, içeriği burada yorumlanmaz (CLAUDE.md).
 */
export function setAuthToken(token: string | null) {
  authToken = token
}

/** Refresh jetonunu istemciye tanıtır (null = refresh yok). authSlice çağırır. */
export function setRefreshToken(token: string | null) {
  refreshTokenValue = token
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  if (authToken) config.headers.Authorization = `Bearer ${authToken}`
  return config
})

/**
 * Access jetonu 401 olunca refresh jetonuyla yeni bir çift alır. Tek-uçuş (single
 * flight): eşzamanlı 401'ler tek bir refresh çağrısını paylaşır — rotation gereği
 * refresh jetonu tek kullanımlıktır, paralel refresh'ler birbirini geçersiz kılardı.
 * apiClient DEĞİL çıplak axios kullanılır: /auth/refresh'in kendi 401'i bu
 * interceptor'a geri düşüp sonsuz döngü yaratmasın. Başarısızlıkta null döner.
 */
let refreshInFlight: Promise<string | null> | null = null

function performTokenRefresh(): Promise<string | null> {
  if (!refreshTokenValue) return Promise.resolve(null)
  if (!refreshInFlight) {
    const base = apiClient.defaults.baseURL ?? ''
    refreshInFlight = axios
      .post<TokenPair>(`${base}/api/v1/auth/refresh`, { refreshToken: refreshTokenValue })
      .then((res) => {
        const pair = res.data
        authToken = pair.token
        refreshTokenValue = pair.refreshToken
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent<TokenPair>(TOKENS_REFRESHED_EVENT, { detail: pair }))
        }
        return pair.token
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

/**
 * JSON beklenen çağrıda gövde ham string kaldıysa yanıt geçersizdir — tipik
 * neden: MSW devre dışıyken (ör. hard refresh service worker'ı baypas eder)
 * Vite SPA fallback'inin 200 + text/html index.html dönmesi. 204/boş gövdeler
 * (logout, chat delete) ve content-type'ı JSON olan meşru string'ler serbesttir.
 */
function isUnexpectedNonJson(response: AxiosResponse): boolean {
  if (response.status === 204 || response.data == null || response.data === '') return false
  const contentType = String(response.headers['content-type'] ?? '')
  return typeof response.data === 'string' && !contentType.includes('json')
}

apiClient.interceptors.response.use(
  (response) => {
    if (isUnexpectedNonJson(response)) {
      const apiError: ApiError = {
        status: response.status,
        message:
          'Sunucudan beklenen JSON yerine farklı bir yanıt alındı. Geliştirme ' +
          'ortamında mock servis (MSW) devrede olmayabilir ya da API adresi hatalı ' +
          'yapılandırılmış olabilir — sayfayı yenileyip tekrar deneyin.',
      }
      return Promise.reject(apiError)
    }
    return response
  },
  async (error: AxiosError<{ message?: string }>) => {
    const status = error.response?.status ?? null
    const original = error.config as RetriableRequestConfig | undefined
    const url = original?.url ?? ''

    // Elimizde jeton varken gelen 401 = oturum düşmüş (süre dolumu / geçersiz jeton).
    // login/register/refresh'in kendi 401'i (hatalı şifre / geçersiz refresh) oturum
    // düşmesi değildir — refresh yeni bir jeton çiftini denemez, çağıran ele alır.
    const isAuthEndpoint =
      url.includes('/auth/login') ||
      url.includes('/auth/register') ||
      url.includes('/auth/refresh')

    if (status === 401 && authToken && !isAuthEndpoint) {
      // Bir kez sessiz refresh dene, sonra isteği şeffafça tekrarla.
      if (original && !original._retried && refreshTokenValue) {
        original._retried = true
        const newToken = await performTokenRefresh()
        if (newToken) {
          original.headers.Authorization = `Bearer ${newToken}`
          return apiClient(original)
        }
      }
      // Refresh jetonu yok ya da refresh başarısız → oturum gerçekten bitti.
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
      }
    }

    const normalized: ApiError = {
      status,
      message:
        error.response?.data?.message ?? error.message ?? 'Beklenmeyen bir hata oluştu.',
    }
    return Promise.reject(normalized)
  },
)
