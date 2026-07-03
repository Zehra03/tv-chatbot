import { useMutation } from '@tanstack/react-query'
import { reservationApi, type ApiError, type CreateReservationRequest, type ReservationPreview } from '@/api'

/**
 * POST /api/v1/reservations/preview — onay öncesi, backend'in hesapladığı
 * özet + toplam tutar (docs/frontend-architecture.md §9). Yazma yapmaz;
 * fiyat burada asla istemcide hesaplanmaz.
 */
export function useReservationPreview() {
  return useMutation<ReservationPreview, ApiError, CreateReservationRequest>({
    mutationFn: (request) => reservationApi.preview(request),
  })
}
