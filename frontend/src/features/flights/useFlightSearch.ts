import { useQuery } from '@tanstack/react-query'
import { flightApi, type ApiError } from '@/api'
import type { FlightProduct, FlightSearchCriteria } from '@/types'

/**
 * Kritere göre key'lenmiş uçuş araması (docs/frontend-architecture.md §5) —
 * aynı kriterle tekrar arama cache'ten döner; kriter girilmeden çalışmaz.
 */
export function useFlightSearch(criteria: FlightSearchCriteria | null) {
  return useQuery<FlightProduct[], ApiError>({
    queryKey: ['flights', 'search', criteria],
    queryFn: () => flightApi.search(criteria as FlightSearchCriteria),
    enabled: criteria !== null,
  })
}
