import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { toast } from 'sonner'
import { reservationApi, type ApiError, type CancelRequest, type CancelResult } from '@/api'
import { RESERVATIONS_KEY } from '@/features/reservation/useReservations'

/**
 * PATCH /api/v1/reservations/{id}/cancel — TourVisio üzerinden iptal (§9). Detay ekranındaki
 * `cancellationOptions`'tan seçilen sebep (`reasonId`) + opsiyonel servislerle çağrılır.
 * Her iki dalda da ['reservations'] öneki invalidate edilir (liste + detay tazelenir).
 *
 * 202 (`pending`) BAŞARI DEĞİLDİR: backend TourVisio'dan yanıt alamadığını, iptalin geçmiş de
 * olabileceğini geçmemiş de olabileceğini söylüyor. Eskiden bu dal da yeşil "Rezervasyon iptal
 * edildi." toast'ı basıyordu — kullanıcı iptal olduğunu sanıp takibi bırakıyor, rezervasyon
 * canlı kalıyordu. Belirsiz dalda backend'in kendi mesajı gösterilir ("…tekrar göndermeyin;
 * birkaç dakika içinde durumu kontrol edin"); o metin bu senaryo için özellikle yazılmıştır.
 */
export function useCancelReservation(id: number) {
  const queryClient = useQueryClient()
  return useMutation<CancelResult, ApiError, CancelRequest>({
    mutationFn: (body) => reservationApi.cancel(id, body),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
      if (result.kind === 'cancelled') {
        toast.success('Rezervasyon iptal edildi.')
      } else {
        toast.warning(result.outcome.message, { duration: 10000 })
      }
    },
    onError: (error) => {
      toast.error(`İptal edilemedi: ${apiErrorMessage(error)}`)
    },
  })
}
