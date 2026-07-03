import { Link, useNavigate } from 'react-router-dom'
import { motion, type Variants } from 'framer-motion'
import { CalendarCheck, LogOut, Mail, MessagesSquare, UserRound } from 'lucide-react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { logout } from '@/features/auth/authSlice'
import { useReservations } from '@/features/reservation/useReservations'

/**
 * /profile — hesap sayfası (kontrollü açık bölge). Oturum bilgisi Redux'ta
 * yaşayan mock kullanıcıdan okunur (docs/frontend-architecture.md §5); gerçek
 * kimlik backend'dedir, burada sır tutulmaz. Rezervasyon sayısı mevcut
 * ['reservations'] query'sinden gelir. Açık bölge dili: primary vurgular +
 * marka gradyan şeridi; bölümler kademeli belirir.
 */
const listVariants: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08 } },
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: 'easeOut' } },
}

export function ProfilePage() {
  const user = useAppSelector((s) => s.auth.user)
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const reservations = useReservations()

  // ProtectedRoute garantisi — yine de tip daraltma için erken çıkış.
  if (!user) return null

  const displayName = user.name ?? user.email.split('@')[0]
  const initials = displayName
    .split(/\s+/)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .slice(0, 2)
    .join('')

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login', { replace: true })
  }

  return (
    <motion.div
      variants={listVariants}
      initial="hidden"
      animate="show"
      className="mx-auto max-w-2xl space-y-6"
    >
      <motion.div variants={itemVariants}>
        <h1 className="text-2xl font-bold">Profil</h1>
        <div
          aria-hidden="true"
          className="mt-1.5 h-1 w-16 rounded-full bg-gradient-to-r from-brand-blue to-brand-teal"
        />
        <p className="mt-2 text-sm text-muted-foreground">
          Hesap bilgileriniz ve hızlı bağlantılar.
        </p>
      </motion.div>

      <motion.div variants={itemVariants}>
        <Card>
          <CardHeader className="flex-row items-center gap-4 space-y-0">
            <div
              aria-hidden="true"
              className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-blue to-brand-teal text-xl font-bold text-white ring-4 ring-primary/10"
            >
              {initials}
            </div>
            <div className="min-w-0 space-y-1">
              {/* Misafirde ad zaten "Misafir" — ayrıca rozet basmak tekrar olur;
                  oturum türü aşağıdaki bilgi listesinde gösteriliyor. */}
              <CardTitle className="truncate text-lg">{displayName}</CardTitle>
              <CardDescription className="truncate">
                {user.email || 'Misafir oturumu'}
              </CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div className="flex items-center gap-3">
                <span
                  aria-hidden="true"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
                >
                  <Mail className="h-4 w-4" />
                </span>
                <div className="min-w-0">
                  <dt className="text-muted-foreground">E-posta</dt>
                  {/* Misafir oturumunda e-posta yok — boş satır yerine tire. */}
                  <dd className="truncate font-medium">{user.email || '—'}</dd>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span
                  aria-hidden="true"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
                >
                  <UserRound className="h-4 w-4" />
                </span>
                <div>
                  <dt className="text-muted-foreground">Oturum türü</dt>
                  <dd className="font-medium">{user.guest ? 'Misafir' : 'Üye'}</dd>
                </div>
              </div>
            </dl>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div variants={itemVariants} className="grid gap-4 sm:grid-cols-2">
        <Card className="transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md">
          <CardHeader>
            <CardTitle className="flex items-center gap-2.5 text-base">
              <span
                aria-hidden="true"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-brand-blue to-brand-teal text-white shadow-sm"
              >
                <CalendarCheck className="h-4 w-4" />
              </span>
              Rezervasyonlarım
            </CardTitle>
            <CardDescription>
              {reservations.data ? (
                <span className="font-semibold text-primary">
                  {reservations.data.length} rezervasyon
                </span>
              ) : (
                'Rezervasyonlarınızı görüntüleyin.'
              )}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button
              asChild
              variant="outline"
              size="sm"
              className="transition-colors hover:border-primary/40 hover:text-primary"
            >
              <Link to="/reservations">Rezervasyonlara git</Link>
            </Button>
          </CardContent>
        </Card>

        <Card className="transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md">
          <CardHeader>
            <CardTitle className="flex items-center gap-2.5 text-base">
              <span
                aria-hidden="true"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-brand-blue to-brand-teal text-white shadow-sm"
              >
                <MessagesSquare className="h-4 w-4" />
              </span>
              Sohbet
            </CardTitle>
            <CardDescription>Aramaya asistanla devam edin.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button
              asChild
              variant="outline"
              size="sm"
              className="transition-colors hover:border-primary/40 hover:text-primary"
            >
              <Link to="/chat">Sohbete dön</Link>
            </Button>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div variants={itemVariants}>
        <Button
          variant="outline"
          onClick={handleLogout}
          className="gap-2 transition-colors hover:border-destructive/40 hover:text-destructive"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          Çıkış yap
        </Button>
      </motion.div>
    </motion.div>
  )
}
