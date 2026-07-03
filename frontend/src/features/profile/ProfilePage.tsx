import { Link, useNavigate } from 'react-router-dom'
import { CalendarCheck, LogOut, Mail, MessagesSquare, UserRound } from 'lucide-react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { logout } from '@/features/auth/authSlice'
import { useReservations } from '@/features/reservation/useReservations'

/**
 * /profile — hesap sayfası (kontrollü açık bölge). Oturum bilgisi Redux'ta
 * yaşayan mock kullanıcıdan okunur (docs/frontend-architecture.md §5); gerçek
 * kimlik backend'dedir, burada sır tutulmaz. Rezervasyon sayısı mevcut
 * ['reservations'] query'sinden gelir.
 */
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
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Profil</h1>
        <p className="text-sm text-muted-foreground">
          Hesap bilgileriniz ve hızlı bağlantılar.
        </p>
      </div>

      <Card>
        <CardHeader className="flex-row items-center gap-4 space-y-0">
          <div
            aria-hidden="true"
            className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-blue to-brand-teal text-xl font-bold text-white"
          >
            {initials}
          </div>
          <div className="min-w-0 space-y-1">
            <CardTitle className="flex flex-wrap items-center gap-2 text-lg">
              <span className="truncate">{displayName}</span>
              {user.guest && <Badge variant="secondary">Misafir</Badge>}
            </CardTitle>
            <CardDescription className="truncate">
              {user.email || 'Misafir oturumu'}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="flex items-center gap-2">
              <Mail className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0">
                <dt className="text-muted-foreground">E-posta</dt>
                {/* Misafir oturumunda e-posta yok — boş satır yerine tire. */}
                <dd className="truncate font-medium">{user.email || '—'}</dd>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <UserRound className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              <div>
                <dt className="text-muted-foreground">Oturum türü</dt>
                <dd className="font-medium">{user.guest ? 'Misafir' : 'Üye'}</dd>
              </div>
            </div>
          </dl>
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <CalendarCheck className="h-4 w-4 text-primary" aria-hidden />
              Rezervasyonlarım
            </CardTitle>
            <CardDescription>
              {reservations.data
                ? `${reservations.data.length} rezervasyon`
                : 'Rezervasyonlarınızı görüntüleyin.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline" size="sm">
              <Link to="/reservations">Rezervasyonlara git</Link>
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <MessagesSquare className="h-4 w-4 text-primary" aria-hidden />
              Sohbet
            </CardTitle>
            <CardDescription>Aramaya asistanla devam edin.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline" size="sm">
              <Link to="/chat">Sohbete dön</Link>
            </Button>
          </CardContent>
        </Card>
      </div>

      <Button variant="outline" onClick={handleLogout} className="gap-2">
        <LogOut className="h-4 w-4" aria-hidden />
        Çıkış yap
      </Button>
    </div>
  )
}
