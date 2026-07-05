import axios, { type AxiosError, type AxiosResponse } from 'axios'

/**
 * Tek Axios instance — tüm backend çağrıları buradan geçer.
 * - baseURL env'den gelir; MSW aktifken boş bırakılır → istekler aynı origin'e
 *   gider ve mock worker tarafından yakalanır.
 * - Response interceptor tüm hataları tek tip `ApiError`'a normalize eder;
 *   JSON beklenirken ham string dönen yanıtlar da ApiError'a çevrilir.
 * - Burada HİÇBİR API anahtarı YOK; AI/TourVisio kimlik bilgileri backend'de (CLAUDE.md).
 */
export interface ApiError {
  status: number | null
  message: string
}

/**
 * Jetonlu bir istek 401 dönünce yayınlanan olay — SessionManager (providers.tsx)
 * dinler ve Redux oturumunu kapatır. Store'u buradan import etmek döngü yaratırdı;
 * olay köprüsü katmanları ayrık tutar.
 */
export const UNAUTHORIZED_EVENT = 'pax:unauthorized'

let authToken: string | null = null

/**
 * Oturum jetonunu istemciye tanıtır (null = oturum kapalı). Tek çağıran
 * authSlice'tır; jeton OPAK'tır, içeriği burada yorumlanmaz (CLAUDE.md).
 */
export function setAuthToken(token: string | null) {
  authToken = token
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
  (error: AxiosError<{ message?: string }>) => {
    const status = error.response?.status ?? null

    // Elimizde jeton varken gelen 401 = oturum düşmüş (süre dolumu / geçersiz
    // jeton). Login/register'ın kendi 401'i (hatalı şifre) oturum düşmesi
    // değildir — o formda inline gösterilir.
    const url = error.config?.url ?? ''
    const isAuthAttempt = url.includes('/auth/login') || url.includes('/auth/register')
    if (status === 401 && authToken && !isAuthAttempt && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }

    const normalized: ApiError = {
      status,
      message:
        error.response?.data?.message ?? error.message ?? 'Beklenmeyen bir hata oluştu.',
    }
    return Promise.reject(normalized)
  },
)
