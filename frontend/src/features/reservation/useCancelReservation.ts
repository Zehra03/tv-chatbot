import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { reservationApi, type ApiError, type CancelRequest, type OutcomeResponse } from '@/api'

/**
 * PATCH /api/v1/reservations/{id}/cancel — TourVisio üzerinden iptal (§9). Detay ekranındaki
 * `cancellationOptions`'tan seçilen sebep (`reasonId`) + opsiyonel servislerle çağrılır. Başarıda
 * ['reservations'] öneki invalidate edilir (liste + bu detay 'cancelled' olarak tazelenir).
 */
export function useCancelReservation(id: number) {
  const queryClient = useQueryClient()
  return useMutation<OutcomeResponse, ApiError, CancelRequest>({
    mutationFn: (body) => reservationApi.cancel(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      toast.success('Rezervasyon iptal edildi.')
    },
    onError: (error) => {
      toast.error(`İptal edilemedi: ${error.message}`)
    },
  })
}
