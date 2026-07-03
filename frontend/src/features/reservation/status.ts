import type { ReservationStatus } from '@/types'
import type { BadgeProps } from '@/components/ui/badge'

/** Rezervasyon durumunun Türkçe etiketi ve rozet varyantı — form sonucu,
 * liste ve detay ekranları aynı eşlemeyi paylaşır. */
export const RESERVATION_STATUS_LABELS: Record<ReservationStatus, string> = {
  pending: 'Beklemede',
  confirmed: 'Onaylandı',
  cancelled: 'İptal edildi',
  failed: 'Başarısız',
}

export function reservationStatusVariant(status: ReservationStatus): BadgeProps['variant'] {
  switch (status) {
    case 'confirmed':
      return 'default'
    case 'pending':
      return 'secondary'
    case 'cancelled':
      return 'outline'
    case 'failed':
      return 'destructive'
  }
}
