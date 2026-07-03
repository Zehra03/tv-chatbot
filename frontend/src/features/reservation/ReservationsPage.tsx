import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Hotel, Plane } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { useReservations } from '@/features/reservation/useReservations'
import {
  RESERVATION_STATUS_LABELS,
  reservationStatusVariant,
} from '@/features/reservation/status'
import { formatDate, formatPrice } from '@/utils/format'

/**
 * /reservations — rezervasyon listesi (docs/frontend-architecture.md §3):
 * no, tip, tarih, misafir, toplam, durum sütunları + detay butonu.
 * Veri React Query'den gelir; oluşturma sonrası invalidation ile tazelenir.
 * Açık bölge dili: primary vurgular + marka gradyan şeridi; satırlar
 * kademeli belirir (gece uçuşu tarafındaki stagger'ın açık karşılığı).
 */
export function ReservationsPage() {
  const { data, isError, isFetching, error, refetch } = useReservations()

  return (
    // Kardeş sayfalar (detay/form/profil) gibi ortalı — tablo uçtan uca yayılmaz.
    <div className="mx-auto max-w-5xl space-y-6">
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: 'easeOut' }}
      >
        <h1 className="text-2xl font-bold">Rezervasyonlarım</h1>
        <div
          aria-hidden="true"
          className="mt-1.5 h-1 w-16 rounded-full bg-gradient-to-r from-brand-blue to-brand-teal"
        />
        <p className="mt-2 text-sm text-muted-foreground">
          Geçmiş ve bekleyen rezervasyonlarınız.
        </p>
      </motion.div>

      {isFetching && !data && <LoadingState label="Yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      )}

      {data && data.length === 0 && (
        <EmptyState>
          Henüz rezervasyonunuz yok —{' '}
          <Link to="/chat" className="text-primary underline-offset-2 hover:underline">
            sohbetten arama yaparak
          </Link>{' '}
          başlayabilirsiniz.
        </EmptyState>
      )}

      {data && data.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, ease: 'easeOut', delay: 0.08 }}
          className="overflow-x-auto rounded-xl border shadow-sm"
        >
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-primary/5 text-left">
                <th scope="col" className="p-3 font-semibold">No</th>
                <th scope="col" className="p-3 font-semibold">Tip</th>
                <th scope="col" className="p-3 font-semibold">Tarih</th>
                <th scope="col" className="p-3 font-semibold">Misafir</th>
                <th scope="col" className="p-3 font-semibold">Toplam</th>
                <th scope="col" className="p-3 font-semibold">Durum</th>
                {/* relative: sr-only (absolute) hücre içinde kalsın, dokümanı genişletmesin. */}
                <th scope="col" className="relative p-3">
                  <span className="sr-only">İşlemler</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.map((r, i) => (
                <motion.tr
                  key={r.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.25, ease: 'easeOut', delay: 0.15 + i * 0.06 }}
                  className="border-b transition-colors even:bg-muted/20 last:border-0 hover:bg-primary/5"
                >
                  <td className="p-3 font-mono font-medium">{r.reservationNumber}</td>
                  <td className="p-3">
                    <span className="inline-flex items-center gap-2">
                      <span
                        aria-hidden="true"
                        className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
                      >
                        {r.productType === 'hotel' ? (
                          <Hotel className="h-3.5 w-3.5" />
                        ) : (
                          <Plane className="h-3.5 w-3.5" />
                        )}
                      </span>
                      {r.productType === 'hotel' ? 'Otel' : 'Uçuş'}
                    </span>
                  </td>
                  <td className="p-3">{formatDate(r.reservationDate)}</td>
                  <td className="p-3">{r.leadGuestName ?? '—'}</td>
                  <td className="p-3 font-semibold">{formatPrice(r.totalAmount, r.currency)}</td>
                  <td className="p-3">
                    <Badge variant={reservationStatusVariant(r.status)}>
                      {RESERVATION_STATUS_LABELS[r.status]}
                    </Badge>
                  </td>
                  <td className="p-3 text-right">
                    <Button
                      asChild
                      size="sm"
                      variant="outline"
                      className="transition-colors hover:border-primary/40 hover:text-primary"
                    >
                      <Link to={`/reservations/${r.id}`}>Detay</Link>
                    </Button>
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </motion.div>
      )}
    </div>
  )
}
