import { useQuery } from '@tanstack/react-query'
import { reservationApi, type ApiError } from '@/api'
import type { Reservation } from '@/types'

/**
 * GET /api/v1/reservations — rezervasyon listesi (§5). Query key
 * ['reservations'] önekiyle başlar; useCreateReservation başarıda bu öneki
 * invalidate ettiğinden liste otomatik tazelenir.
 */
export function useReservations() {
  return useQuery<Reservation[], ApiError>({
    queryKey: ['reservations'],
    queryFn: () => reservationApi.list(),
  })
}
