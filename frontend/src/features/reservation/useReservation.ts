import { useQuery } from '@tanstack/react-query'
import { reservationApi, type ApiError } from '@/api'
import type { Reservation } from '@/types'

/**
 * GET /api/v1/reservations/{id} — tek rezervasyonun tam dökümü (§5).
 * ['reservations', id] key'i liste önekini paylaşır; oluşturma sonrası
 * invalidation detayı da tazeler.
 */
export function useReservation(id: string | undefined) {
  return useQuery<Reservation, ApiError>({
    queryKey: ['reservations', id],
    queryFn: () => reservationApi.get(id as string),
    enabled: !!id,
  })
}
