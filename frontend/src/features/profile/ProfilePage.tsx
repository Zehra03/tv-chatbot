import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion, type Variants } from 'framer-motion'
import { toast } from 'sonner'
import { CalendarCheck, LogOut, Mail, MessagesSquare, Pencil, UserRound } from 'lucide-react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { SplitText } from '@/components/SplitText'
import { authApi, type ApiError } from '@/api'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { logout, userRefreshed } from '@/features/auth/authSlice'
import { useReservations } from '@/features/reservation/useReservations'

/** Kaba e-posta biçim denetimi — backend @Email / MSW handler paritesi. */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

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

  // E-posta satır-içi düzenleme durumu (yalnızca üye oturumunda kullanılır).
  const [editingEmail, setEditingEmail] = useState(false)
  const [emailDraft, setEmailDraft] = useState('')
  const [emailError, setEmailError] = useState('')
  const [savingEmail, setSavingEmail] = useState(false)

  // ProtectedRoute garantisi — yine de tip daraltma için erken çıkış.
  if (!user) return null

  const startEditEmail = () => {
    setEmailDraft(user.email)
    setEmailError('')
    setEditingEmail(true)
  }

  const cancelEditEmail = () => {
    setEditingEmail(false)
    setEmailError('')
  }

  const saveEmail = async () => {
    if (savingEmail) return
    const next = emailDraft.trim()
    if (!EMAIL_RE.test(next)) {
      setEmailError('Geçerli bir e-posta girin.')
      return
    }
    // Değişiklik yoksa boşuna backend'e gitme.
    if (next.toLowerCase() === user.email.toLowerCase()) {
      cancelEditEmail()
      return
    }
    setSavingEmail(true)
    setEmailError('')
    try {
      const updated = await authApi.updateEmail({ email: next })
      // Backend gerçek kaynaktır; dönen kullanıcıyı Redux'a (ve localStorage'a) yaz.
      dispatch(userRefreshed(updated))
      setEditingEmail(false)
      toast.success('E-posta güncellendi.')
    } catch (err) {
      setEmailError((err as ApiError).message || 'E-posta güncellenemedi — tekrar deneyin.')
    } finally {
      setSavingEmail(false)
    }
  }

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
        <SplitText
          text="Profil"
          tag="h1"
          textAlign="left"
          className="text-2xl font-bold text-white"
          delay={40}
          duration={0.8}
        />
        <div
          aria-hidden="true"
          className="mt-1.5 h-1 w-16 rounded-full bg-gradient-to-r from-brand-blue to-brand-teal"
        />
        <p className="mt-2 text-sm text-brand-ice/70">
          Hesap bilgileriniz ve hızlı bağlantılar.
        </p>
      </motion.div>

      <motion.div variants={itemVariants}>
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardHeader className="flex-row items-center gap-4 space-y-0">
            <div
              aria-hidden="true"
              className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-blue to-brand-teal text-xl font-bold text-white ring-4 ring-white/15"
            >
              {initials}
            </div>
            <div className="min-w-0 space-y-1">
              {/* Misafirde ad zaten "Misafir" — ayrıca rozet basmak tekrar olur;
                  oturum türü aşağıdaki bilgi listesinde gösteriliyor. */}
              <CardTitle className="truncate text-lg">{displayName}</CardTitle>
              <CardDescription className="truncate text-brand-ice/70">
                {user.email || 'Misafir oturumu'}
              </CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div className="flex items-start gap-3">
                <span
                  aria-hidden="true"
                  className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-teal/15 text-brand-teal"
                >
                  <Mail className="h-4 w-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <dt className="text-brand-ice/70">E-posta</dt>
                  {editingEmail ? (
                    <dd className="mt-1 space-y-2">
                      <input
                        type="email"
                        aria-label="E-posta"
                        value={emailDraft}
                        autoFocus
                        disabled={savingEmail}
                        onChange={(e) => setEmailDraft(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault()
                            void saveEmail()
                          } else if (e.key === 'Escape') {
                            cancelEditEmail()
                          }
                        }}
                        className="w-full rounded-md border border-white/20 bg-white/10 px-2.5 py-1.5 text-sm text-white placeholder:text-brand-ice/40 focus:border-brand-teal focus:outline-none focus:ring-1 focus:ring-brand-teal disabled:opacity-60"
                      />
                      {emailError && (
                        <p role="alert" className="text-xs text-destructive">
                          {emailError}
                        </p>
                      )}
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => void saveEmail()}
                          disabled={savingEmail}
                          className="inline-flex items-center rounded-md bg-gradient-to-r from-brand-blue to-brand-teal px-3 py-1.5 text-xs font-semibold text-white transition hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {savingEmail ? 'Kaydediliyor…' : 'Kaydet'}
                        </button>
                        <button
                          type="button"
                          onClick={cancelEditEmail}
                          disabled={savingEmail}
                          className="inline-flex items-center rounded-md px-3 py-1.5 text-xs font-medium text-brand-ice/70 transition hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal disabled:opacity-60"
                        >
                          İptal
                        </button>
                      </div>
                    </dd>
                  ) : (
                    <dd className="flex items-center gap-2">
                      {/* Misafir oturumunda e-posta yok — boş satır yerine tire. */}
                      <span className="truncate font-medium">{user.email || '—'}</span>
                      {!user.guest && (
                        <button
                          type="button"
                          onClick={startEditEmail}
                          aria-label="E-postayı düzenle"
                          className="shrink-0 text-brand-ice/60 transition-colors hover:text-brand-teal focus-visible:text-brand-teal focus-visible:outline-none"
                        >
                          <Pencil className="h-3.5 w-3.5" aria-hidden />
                        </button>
                      )}
                    </dd>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span
                  aria-hidden="true"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-teal/15 text-brand-teal"
                >
                  <UserRound className="h-4 w-4" />
                </span>
                <div>
                  <dt className="text-brand-ice/70">Oturum türü</dt>
                  <dd className="font-medium">{user.guest ? 'Misafir' : 'Üye'}</dd>
                </div>
              </div>
            </dl>
          </CardContent>
        </Card>
      </motion.div>

      <motion.div variants={itemVariants} className="grid gap-4 sm:grid-cols-2">
        <Card className="glass-card h-full border-white/15 bg-white/10 text-white transition-all hover:border-brand-teal/40 hover:bg-white/[0.14]">
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
            <CardDescription className="text-brand-ice/70">
              {reservations.data ? (
                <span className="font-semibold text-brand-teal">
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
            >
              <Link to="/reservations">Rezervasyonlara git</Link>
            </Button>
          </CardContent>
        </Card>

        <Card className="glass-card h-full border-white/15 bg-white/10 text-white transition-all hover:border-brand-teal/40 hover:bg-white/[0.14]">
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
            <CardDescription className="text-brand-ice/70">
              Aramaya asistanla devam edin.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button
              asChild
              variant="outline"
              size="sm"
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
          className="gap-2 hover:border-destructive hover:bg-destructive/20 hover:text-white"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          Çıkış yap
        </Button>
      </motion.div>
    </motion.div>
  )
}
