import { useQuery } from '@tanstack/react-query'
import { reservationApi, type ApiError } from '@/api'
import type { ReservationDetail } from '@/types'
import { useAppSelector } from '@/app/hooks'
import { selectIdentity } from '@/features/auth/selectors'
import { RESERVATIONS_KEY } from '@/features/reservation/useReservations'

/**
 * GET /api/v1/reservations/{id} — tek rezervasyonun tam dökümü + canlı iptal seçenekleri (§5).
 * Key liste ön-ekini ve kimliği paylaşır ([...RESERVATIONS_KEY, identity, id]) — onay/iptal
 * sonrası ön-ek invalidation'ı detayı da tazeler, kimlik değişince girdi çakışmaz.
 * URL param'ı string gelir; backend bigint beklediğinden sayıya çevrilir.
 */
export function useReservation(id: string | undefined) {
  const identity = useAppSelector(selectIdentity)
  return useQuery<ReservationDetail, ApiError>({
    queryKey: [...RESERVATIONS_KEY, identity ?? 'anon', id],
    queryFn: () => reservationApi.get(Number(id)),
    enabled: !!id,
  })
}
