import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { reservationApi, type ApiError, type ConfirmRequest, type ConfirmResult } from '@/api'
import { useAppDispatch } from '@/app/hooks'
import { clearDraft } from '@/features/reservation/reservationDraftSlice'
import { RESERVATIONS_KEY } from '@/features/reservation/useReservations'
import { apiErrorMessage } from '@/lib/apiErrorMessage'

/**
 * POST /api/v1/reservations — kesin onay (§9). Yalnızca formdaki açık onaydan sonra çağrılır;
 * chatbot bu mutation'a asla dokunmaz. Oluştuğunda (`created`/`createdFallback`) rezervasyon
 * sorguları invalidate edilir (liste tazelenir) ve taslak temizlenir. Uyarı (`needsConfirmation`)
 * ve belirsiz (`pending`) dalları BAŞARI değildir — taslak korunur, ekran `confirm.data`'dan ele alır.
 * Ağ/iş hataları (410/403/409/422/502/500) `onError`'a düşer.
 */
export function useConfirmReservation() {
  const queryClient = useQueryClient()
  const dispatch = useAppDispatch()

  return useMutation<ConfirmResult, ApiError, ConfirmRequest>({
    mutationFn: (request) => reservationApi.confirm(request),
    onSuccess: (result) => {
      if (result.kind === 'created') {
        queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
        dispatch(clearDraft())
        toast.success(`Rezervasyon alındı — ${result.reservation.reservationNumber}`)
      } else if (result.kind === 'createdFallback') {
        queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
        dispatch(clearDraft())
        toast.success('Rezervasyon oluşturuldu.')
      }
      // needsConfirmation / pending → başarı değil; toast yok, taslak durur, ekran karar verir.
    },
    onError: (error) => {
      toast.error(`Rezervasyon oluşturulamadı: ${apiErrorMessage(error)}`)
    },
  })
}
