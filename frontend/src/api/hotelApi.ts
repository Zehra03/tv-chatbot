import { apiClient } from './client'
import type { HotelSearchCriteria, HotelProduct } from '@/types'

/**
 * Otel arama endpoint'i (docs/frontend-architecture.md §6).
 *   POST /api/v1/hotels/search  → HotelProduct[]
 * Sonuçlar TourVisio'dan (mock'ta fixture); fiyat/uygunluk uydurulmaz.
 */
export const hotelApi = {
  async search(criteria: HotelSearchCriteria): Promise<HotelProduct[]> {
    const res = await apiClient.post<HotelProduct[]>('/api/v1/hotels/search', criteria)
    return res.data
  },
}
