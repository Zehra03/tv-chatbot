import { useMutation, useQueryClient } from '@tanstack/react-query'
import { reservationApi, type ApiError, type CreateReservationRequest } from '@/api'
import { useAppDispatch } from '@/app/hooks'
import { clearDraft } from '@/features/reservation/reservationDraftSlice'
import type { Reservation } from '@/types'

/**
 * POST /api/v1/reservations — kontrollü rezervasyon oluşturma (§9). Yalnızca
 * formdaki açık onaydan sonra çağrılır; chatbot bu mutation'a asla dokunmaz.
 * Başarıda rezervasyon sorguları invalidate edilir (liste tazelenir) ve
 * taslak temizlenir.
 */
export function useCreateReservation() {
  const queryClient = useQueryClient()
  const dispatch = useAppDispatch()

  return useMutation<Reservation, ApiError, CreateReservationRequest>({
    mutationFn: (request) => reservationApi.create(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      dispatch(clearDraft())
    },
  })
}
