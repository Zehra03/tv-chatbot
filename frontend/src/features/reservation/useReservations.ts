import { useQuery } from '@tanstack/react-query'
import { reservationApi, type ApiError } from '@/api'
import type { ReservationSummary } from '@/types'

/**
 * GET /api/v1/reservations — mevcut kullanıcının rezervasyonları (yeni→eski). Query key
 * ['reservations'] önekiyle başlar; onay/iptal başarıda bu öneki invalidate ettiğinden liste
 * otomatik tazelenir.
 */
export function useReservations() {
  return useQuery<ReservationSummary[], ApiError>({
    queryKey: ['reservations'],
    queryFn: () => reservationApi.list(),
  })
}
