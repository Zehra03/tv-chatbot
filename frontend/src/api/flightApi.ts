import { apiClient } from './client'
import type { FlightSearchCriteria, FlightProduct } from '@/types'

/**
 * Uçuş arama endpoint'i (docs/frontend-architecture.md §6).
 *   POST /api/v1/flights/search  → FlightProduct[]
 * Sonuçlar TourVisio'dan (mock'ta fixture); fiyat/uygunluk uydurulmaz.
 */
export const flightApi = {
  async search(criteria: FlightSearchCriteria): Promise<FlightProduct[]> {
    const res = await apiClient.post<FlightProduct[]>('/api/v1/flights/search', criteria)
    return res.data
  },
}
