import type { ReservationProductType, ReservationStatus } from '@/types'
import type { BadgeProps } from '@/components/ui/badge'

/** Rezervasyon durumunun Türkçe etiketi ve rozet varyantı — form sonucu,
 * liste ve detay ekranları aynı eşlemeyi paylaşır. */
export const RESERVATION_STATUS_LABELS: Record<ReservationStatus, string> = {
  pending: 'Beklemede',
  confirmed: 'Onaylandı',
  cancelled: 'İptal edildi',
  failed: 'Başarısız',
}

/** Ürün tipinin Türkçe etiketi — liste ve detay paylaşır; `combined` = paket (otel + uçuş). */
export const RESERVATION_PRODUCT_TYPE_LABELS: Record<ReservationProductType, string> = {
  hotel: 'Otel',
  flight: 'Uçuş',
  combined: 'Otel + Uçuş',
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
