import { useParams } from 'react-router-dom'
import { PagePlaceholder } from '@/components/PagePlaceholder'

/** /reservations/:id — Rezervasyon detayı. Gerçek implementasyon Epic 7'de. */
export function ReservationDetailPage() {
  const { id } = useParams()
  return <PagePlaceholder title="Rezervasyon detayı" route={`/reservations/${id ?? ':id'}`} />
}
