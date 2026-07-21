import { useQuery } from '@tanstack/react-query'
import { reservationApi, type ApiError } from '@/api'
import type { ReservationSummary } from '@/types'
import { useAppSelector } from '@/app/hooks'
import { selectIdentity } from '@/features/auth/selectors'

/** Rezervasyon query key ön-eki — invalidate bu ön-ekle tüm kimlik varyantlarını
 * (kısmi eşleşme) ve liste+detay girdilerini birlikte kapsar. */
export const RESERVATIONS_KEY = ['reservations'] as const

/**
 * GET /api/v1/reservations — mevcut kullanıcının rezervasyonları (yeni→eski).
 * Query key aktif kimliği taşır (useChatSessions ile aynı örüntü): bir hesabın cache'lenmiş
 * listesi, çıkış yapıp başka hesapla devam edene ASLA gösterilmez — ayrı kimlik = ayrı girdi.
 * Onay/iptal başarıda ['reservations'] öneki invalidate edildiğinden liste otomatik tazelenir.
 */
export function useReservations() {
  const identity = useAppSelector(selectIdentity)
  return useQuery<ReservationSummary[], ApiError>({
    queryKey: [...RESERVATIONS_KEY, identity ?? 'anon'],
    queryFn: () => reservationApi.list(),
  })
}
