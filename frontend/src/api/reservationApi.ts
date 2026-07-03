import { apiClient } from './client'
import type { CurrencyCode, Passenger, ProductType, Reservation } from '@/types'

/**
 * Rezervasyon endpoint'leri (docs/frontend-architecture.md §6 · §9).
 *   POST  /api/v1/reservations/preview     → fiyat/özet önizleme (yazma yok)
 *   POST  /api/v1/reservations             → rezervasyon oluştur
 *   GET   /api/v1/reservations             → liste
 *   GET   /api/v1/reservations/{id}        → detay
 *   PATCH /api/v1/reservations/{id}/cancel → iptal
 * Kontrollü akış: kullanıcı formda açıkça onaylar; chatbot booking yapmaz.
 */

/** Formdan gönderilen oluşturma isteği. Toplam tutarı backend hesaplar (uydurulmaz). */
export interface CreateReservationRequest {
  productType: ProductType
  productId: string
  passengers: Passenger[]
  currency: CurrencyCode
}

/** Onay öncesi önizleme — backend'in hesapladığı özet + tutar. */
export interface ReservationPreview {
  productType: ProductType
  productId: string
  title: string
  summary: string
  totalAmount: number
  currency: CurrencyCode
  passengers: Passenger[]
}

export const reservationApi = {
  async preview(body: CreateReservationRequest): Promise<ReservationPreview> {
    const res = await apiClient.post<ReservationPreview>('/api/v1/reservations/preview', body)
    return res.data
  },

  async create(body: CreateReservationRequest): Promise<Reservation> {
    const res = await apiClient.post<Reservation>('/api/v1/reservations', body)
    return res.data
  },

  async list(): Promise<Reservation[]> {
    const res = await apiClient.get<Reservation[]>('/api/v1/reservations')
    return res.data
  },

  async get(id: string): Promise<Reservation> {
    const res = await apiClient.get<Reservation>(`/api/v1/reservations/${id}`)
    return res.data
  },

  async cancel(id: string): Promise<Reservation> {
    const res = await apiClient.patch<Reservation>(`/api/v1/reservations/${id}/cancel`)
    return res.data
  },
}
