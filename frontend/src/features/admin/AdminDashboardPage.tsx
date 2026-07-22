import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CalendarCheck, Hotel, Plane, Users } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { formatPrice } from '@/utils/format'
import { AdminPageHeader } from './AdminPage'
import { useAdminStats } from './useAdminData'

/**
 * /admin — sistem özeti: toplam rezervasyon, kullanıcı ve ürün tipine göre kırılım.
 *
 * Kartlardaki uçuş/otel sayıları REZERVASYON sayısıdır, envanter değil: bu sistemde uçuş ya da
 * otel kataloğu tutulmuyor, aranabilir ürünler TourVisio'dan canlı geliyor. Sayılar backend'de
 * DB'den okunur — burada hiçbir değer türetilmez ya da uydurulmaz.
 */
function StatCard({
  label,
  value,
  hint,
  icon,
  to,
  delay,
}: {
  label: string
  value: ReactNode
  hint?: string
  icon: ReactNode
  to?: string
  delay: number
}) {
  const body = (
    <Card className="relative h-full p-5 transition-all duration-300 hover:border-primary/40 hover:shadow-soft">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <p className="mt-2 text-3xl font-bold tabular-nums text-foreground">{value}</p>
          {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
        </div>
        <span
          aria-hidden="true"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"
        >
          {icon}
        </span>
      </div>
    </Card>
  )

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: 'easeOut', delay }}
    >
      {to ? (
        <Link
          to={to}
          className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {body}
        </Link>
      ) : (
        body
      )}
    </motion.div>
  )
}

export function AdminDashboardPage() {
  const { data, isError, isFetching, error, refetch } = useAdminStats()

  const byType = data?.reservationsByProductType ?? {}
  // "combined" (otel + uçuş) paket rezervasyonu her iki sayaca da girer: pakette gerçekten
  // bir uçuş VE bir otel vardır, tek bir sepete saymak ikisini de eksik gösterirdi.
  const flights = (byType.flight ?? 0) + (byType.combined ?? 0)
  const hotels = (byType.hotel ?? 0) + (byType.combined ?? 0)

  const revenue = Object.entries(data?.totalRevenueByCurrency ?? {})

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="Dashboard"
        description="Sistemdeki rezervasyon, uçuş ve kullanıcı özeti."
      />

      {isFetching && !data && <LoadingState label="Özet yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={apiErrorMessage(error)} onRetry={() => refetch()} />
      )}

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Toplam rezervasyon"
              value={data.totalReservations}
              icon={<CalendarCheck className="h-5 w-5" />}
              to="/admin/reservations"
              delay={0.05}
            />
            <StatCard
              label="Uçuş rezervasyonu"
              value={flights}
              hint="Paket (otel + uçuş) dahil"
              icon={<Plane className="h-5 w-5" />}
              to="/admin/flights"
              delay={0.1}
            />
            <StatCard
              label="Otel rezervasyonu"
              value={hotels}
              hint="Paket (otel + uçuş) dahil"
              icon={<Hotel className="h-5 w-5" />}
              delay={0.15}
            />
            <StatCard
              label="Kayıtlı kullanıcı"
              value={data.activeUsers}
              icon={<Users className="h-5 w-5" />}
              to="/admin/users"
              delay={0.2}
            />
          </div>

          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: 'easeOut', delay: 0.25 }}
          >
            <Card className="p-5">
              <h2 className="text-sm font-semibold text-foreground">Ciro</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Yalnızca onaylanmış rezervasyonlar, para birimine göre.
              </p>
              {revenue.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">
                  Onaylanmış rezervasyon bulunmuyor.
                </p>
              ) : (
                <ul className="mt-4 flex flex-wrap gap-x-8 gap-y-3">
                  {revenue.map(([currency, amount]) => (
                    <li key={currency}>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">
                        {currency}
                      </p>
                      <p className="text-xl font-bold tabular-nums text-foreground">
                        {formatPrice(amount, currency)}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </motion.div>
        </>
      )}
    </div>
  )
}
