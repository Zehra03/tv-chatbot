import { apiClient } from './client'
import type { HotelSearchCriteria, HotelProduct, HotelLocation } from '@/types'

/**
 * Otel arama endpoint'i (docs/frontend-architecture.md §6).
 *   POST /api/v1/hotels/search     → HotelProduct[]
 *   GET  /api/v1/hotels/locations  → HotelLocation[]  (destination otomatik tamamlama)
 * Sonuçlar TourVisio'dan (mock'ta fixture); fiyat/uygunluk uydurulmaz.
 */
export const hotelApi = {
  async search(criteria: HotelSearchCriteria): Promise<HotelProduct[]> {
    const res = await apiClient.post<HotelProduct[]>('/api/v1/hotels/search', criteria)
    return res.data
  },

  /** Serbest metni ("Ant") TourVisio şehir/bölge önerilerine çevirir; seçilen isim aramaya gider. */
  async locations(q: string): Promise<HotelLocation[]> {
    const res = await apiClient.get<HotelLocation[]>('/api/v1/hotels/locations', {
      params: { q },
    })
    return res.data
  },
}
