import { useMutation } from '@tanstack/react-query'
import { reservationApi, type ApiError, type CreateReservationRequest } from '@/api'
import type { Reservation } from '@/types'

/**
 * POST /api/v1/reservations — kontrollü rezervasyon oluşturma (§9). Yalnızca
 * formdaki açık onaydan sonra çağrılır; chatbot bu mutation'a asla dokunmaz.
 */
export function useCreateReservation() {
  return useMutation<Reservation, ApiError, CreateReservationRequest>({
    mutationFn: (request) => reservationApi.create(request),
  })
}
