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

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
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
    const normalized: ApiError = {
      status: error.response?.status ?? null,
      message:
        error.response?.data?.message ?? error.message ?? 'Beklenmeyen bir hata oluştu.',
    }
    return Promise.reject(normalized)
  },
)
