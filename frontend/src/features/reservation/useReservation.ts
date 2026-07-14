import { useQuery } from '@tanstack/react-query'
import { reservationApi, type ApiError } from '@/api'
import type { ReservationDetail } from '@/types'

/**
 * GET /api/v1/reservations/{id} — tek rezervasyonun tam dökümü + canlı iptal seçenekleri (§5).
 * ['reservations', id] key'i liste önekini paylaşır; onay/iptal sonrası invalidation detayı da tazeler.
 * URL param'ı string gelir; backend bigint beklediğinden sayıya çevrilir.
 */
export function useReservation(id: string | undefined) {
  return useQuery<ReservationDetail, ApiError>({
    queryKey: ['reservations', id],
    queryFn: () => reservationApi.get(Number(id)),
    enabled: !!id,
  })
}
