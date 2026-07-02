import axios, { type AxiosError } from 'axios'

/**
 * Tek Axios instance — tüm backend çağrıları buradan geçer.
 * - baseURL env'den gelir; MSW aktifken boş bırakılır → istekler aynı origin'e
 *   gider ve mock worker tarafından yakalanır.
 * - Response interceptor tüm hataları tek tip `ApiError`'a normalize eder.
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

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string }>) => {
    const normalized: ApiError = {
      status: error.response?.status ?? null,
      message:
        error.response?.data?.message ?? error.message ?? 'Beklenmeyen bir hata oluştu.',
    }
    return Promise.reject(normalized)
  },
)
