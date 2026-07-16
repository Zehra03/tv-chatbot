import { apiClient } from './client'
import type { FlightSearchCriteria, FlightProduct, FlightLocation } from '@/types'

/** Kalkış (origin) ya da varış (destination) alanı için otomatik tamamlama yönü. */
export type LocationDirection = 'departure' | 'arrival'

/**
 * Uçuş arama endpoint'i (docs/frontend-architecture.md §6).
 *   POST /api/v1/flights/search     → FlightProduct[]
 *   GET  /api/v1/flights/locations  → FlightLocation[]  (kalkış/varış otomatik tamamlama)
 * Sonuçlar TourVisio'dan (mock'ta fixture); fiyat/uygunluk uydurulmaz.
 */
export const flightApi = {
  async search(criteria: FlightSearchCriteria): Promise<FlightProduct[]> {
    const res = await apiClient.post<FlightProduct[]>('/api/v1/flights/search', criteria)
    return res.data
  },

  /** Serbest metni ("Ant") TourVisio konum önerilerine çevirir; seçilen `id` aramaya gider. */
  async locations(q: string, direction: LocationDirection): Promise<FlightLocation[]> {
    const res = await apiClient.get<FlightLocation[]>('/api/v1/flights/locations', {
      params: { q, direction },
    })
    return res.data
  },
}
