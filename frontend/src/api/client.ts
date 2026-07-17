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
  /**
   * Makine-okunur hata kodu (varsa). Auth hataları `error` (ör. "EMAIL_ALREADY_EXISTS"),
   * rezervasyon hataları `outcome` (ör. "PREVIEW_EXPIRED", "TOURVISIO_REJECTED") döndürür —
   * ikisi de buraya yansır ki çağıran ekran anlamlı bir mesaj eşleyebilsin.
   */
  code?: string
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
let guestIdValue: string | null = null

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

/**
 * Misafir kimliğini (opak X-Guest-Id) istemciye tanıtır (null = misafir değil).
 * authSlice çağırır. Jetonsuz misafir isteklerinde bu başlık gider; backend bununla
 * misafir oturumlarını sahiplendirir (JWT DEĞİL, düşük-değerli taşıyıcı anahtar).
 */
export function setGuestId(guestId: string | null) {
  guestIdValue = guestId
}

/**
 * Sunucu bağlantıyı kabul edip yanıtı hiç göndermezse (TourVisio asılı, container kilitli, VPN
 * yanıt ortasında düşmüş) axios varsayılanı SONSUZA kadar bekler (`timeout: 0`) — promise hiç
 * settle olmaz, React Query `isFetching`te kalır ve kullanıcı hiç bitmeyen bir spinner görür;
 * hata ekranına asla düşemez. Zaman aşımı `status: null` üretir, yani apiErrorMessage'ın
 * "Sunucuya ulaşılamadı" dalına girer.
 *
 * Tek ve cömert bir değer: buradaki uçların HEPSİ TourVisio ya da AI'a vekillik ediyor, aralarında
 * belirgin "hızlı" bir uç yok. Daha sıkı bir sınır, gerçek bir aramayı/booking'i haksız yere
 * kesme riski taşır; amaç gecikmeyi ayarlamak değil, sonsuz askıyı sınırlamak. Uç bazında
 * ayarlama gerçek gecikme verisiyle yapılmalı.
 */
const REQUEST_TIMEOUT_MS = 60_000

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  // Yetkili kullanıcı → Bearer jeton. Jetonsuz misafir → X-Guest-Id. İkisi bir arada
  // gönderilmez: giriş yapınca authSlice guestId'yi temizler.
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`
  } else if (guestIdValue) {
    config.headers['X-Guest-Id'] = guestIdValue
  }
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
  async (error: AxiosError<{ message?: string; error?: string; outcome?: string }>) => {
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
      code: error.response?.data?.error ?? error.response?.data?.outcome,
    }
    return Promise.reject(normalized)
  },
)
