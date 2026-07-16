import { useQuery } from '@tanstack/react-query'
import { hotelApi, type ApiError } from '@/api'
import type { HotelProduct, HotelSearchCriteria } from '@/types'

/**
 * Kritere göre key'lenmiş otel araması (docs/frontend-architecture.md §5) —
 * aynı kriterle tekrar arama cache'ten döner; kriter girilmeden çalışmaz.
 */
export function useHotelSearch(criteria: HotelSearchCriteria | null) {
  return useQuery<HotelProduct[], ApiError>({
    queryKey: ['hotels', 'search', criteria],
    queryFn: () => hotelApi.search(criteria as HotelSearchCriteria),
    enabled: criteria !== null,
  })
}
