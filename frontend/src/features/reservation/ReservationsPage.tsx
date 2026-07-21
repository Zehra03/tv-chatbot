import { Link } from 'react-router-dom'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { motion } from 'framer-motion'
import { ChevronRight, FileDown, Hotel, Plane } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SplitText } from '@/components/SplitText'
import { useReservations } from '@/features/reservation/useReservations'
import {
  RESERVATION_PRODUCT_TYPE_LABELS,
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
        <SplitText
          text="Rezervasyonlarım"
          tag="h1"
          textAlign="left"
          className="text-2xl font-bold text-foreground"
          delay={40}
          duration={0.8}
        />
        <div
          aria-hidden="true"
          className="mt-1.5 h-1 w-16 rounded-full bg-gradient-to-r from-brand-blue to-brand-steel"
        />
        <p className="mt-2 text-sm text-muted-foreground">
          Geçmiş ve bekleyen rezervasyonlarınız
          {data && data.length > 0 ? ` · ${data.length} kayıt` : ''}.
        </p>
      </motion.div>

      {isFetching && !data && <LoadingState label="Yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={apiErrorMessage(error)} onRetry={() => refetch()} />
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
        // Tablo yerine kart listesi: mobilde satır sütunlara sıkışmaz, her kayıt
        // taranabilir bir kart. Kart tümüyle detaya bağlı (stretched-link); PDF
        // eylemi bağlantının ÜSTÜNDE (z-10) ayrı erişilebilir düğme kalır.
        <ul className="space-y-3">
          {data.map((r, i) => (
            <motion.li
              key={r.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25, ease: 'easeOut', delay: 0.12 + i * 0.06 }}
            >
              <article className="glass-card relative flex flex-col gap-3 p-4 transition-all duration-300 hover:border-primary/40 hover:shadow-soft sm:flex-row sm:items-center sm:gap-4">
                <div className="flex min-w-0 flex-1 items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"
                  >
                    {r.productType === 'flight' ? (
                      <Plane className="h-5 w-5" />
                    ) : (
                      <Hotel className="h-5 w-5" />
                    )}
                  </span>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        to={`/reservations/${r.id}`}
                        className="rounded font-mono text-sm font-semibold text-foreground transition-colors after:absolute after:inset-0 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        {r.reservationNumber}
                      </Link>
                      <Badge variant={reservationStatusVariant(r.status)}>
                        {RESERVATION_STATUS_LABELS[r.status]}
                      </Badge>
                    </div>
                    <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-sm text-muted-foreground">
                      <span>{RESERVATION_PRODUCT_TYPE_LABELS[r.productType]}</span>
                      <span aria-hidden>·</span>
                      <span>{formatDate(r.reservationDate)}</span>
                      {r.leadGuestName && (
                        <>
                          <span aria-hidden>·</span>
                          <span className="text-foreground/80">{r.leadGuestName}</span>
                        </>
                      )}
                    </p>
                  </div>
                </div>

                <div className="flex items-center justify-between gap-3 sm:justify-end">
                  <p className="text-base font-bold text-foreground">
                    {formatPrice(r.totalAmount, r.currency)}
                  </p>
                  <div className="relative z-10 flex shrink-0 items-center gap-1.5">
                    {/* İkon düğme — erişilebilir adı rezervasyon kodunu taşır ki
                        ekran okuyucuda satırlar ayırt edilebilsin. */}
                    <Button
                      asChild
                      size="icon"
                      variant="outline"
                      className="h-8 w-8 border-border bg-muted text-muted-foreground transition-colors hover:border-primary hover:text-foreground"
                    >
                      <Link
                        to={`/reservations/${r.id}/print`}
                        aria-label={`${r.reservationNumber} özetini PDF olarak indir`}
                      >
                        <FileDown className="h-4 w-4" />
                      </Link>
                    </Button>
                    <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden />
                  </div>
                </div>
              </article>
            </motion.li>
          ))}
        </ul>
      )}
    </div>
  )
}
