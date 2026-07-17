import React, { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowRight, Eye, EyeOff, MapPin, Plane, Star, Wifi } from 'lucide-react'
import heroImage from '@/assets/travel-hero.png'
import hotelImage from '@/assets/hotel-room.png'
import { authApi, type ApiError, type AuthResponse } from '@/api'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { guestSessionStarted, sessionStarted } from '@/features/auth/authSlice'
import { Button } from '@/components/ui/button'
import { FloatingInput } from '@/components/ui/floating-input'
import { Logo } from '@/components/Logo'
import { ForgotPasswordModal } from '@/features/auth/ForgotPasswordModal'
import { cn } from '@/lib/utils'

type AuthMode = 'login' | 'register'

interface RegisterData {
  fullName: string
  email: string
  password: string
}

interface AuthScreenProps {
  onLogin?: (email: string, password: string) => void
  onRegister?: (data: RegisterData) => void
  onGuestContinue?: () => void
  onForgotPassword?: () => void
  /** API çağrısı sürerken submit butonları kilitlenir. */
  submitting?: boolean
  /** Backend'den dönen hata (ör. hatalı şifre) — form içi hatalarla aynı yerde gösterilir. */
  errorMessage?: string
  /** Mod değişince üstteki API hatasını temizlemek için. */
  onModeSwitch?: () => void
}

const AuthScreen: React.FC<AuthScreenProps> = ({
  onLogin = () => {},
  onRegister = () => {},
  onGuestContinue = () => {},
  onForgotPassword = () => {},
  submitting = false,
  errorMessage = '',
  onModeSwitch = () => {},
}) => {
  const [mode, setMode] = useState<AuthMode>('login')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')

  const isRegister = mode === 'register'
  // Yerel form hatası öncelikli; yoksa API hatası gösterilir.
  const shownError = error || errorMessage

  const switchMode = (next: AuthMode) => {
    setError('')
    onModeSwitch()
    setMode(next)
  }

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault()
    onLogin(email, password)
  }

  const handleRegister = (e: React.FormEvent) => {
    e.preventDefault()
    if (password !== confirmPassword) {
      setError('Şifreler eşleşmiyor.')
      return
    }
    // Backend kuralıyla aynı (RegisterRequestDto @Size(min = 8)).
    if (password.length < 8) {
      setError('Şifre en az 8 karakter olmalıdır.')
      return
    }
    setError('')
    onRegister({ fullName, email, password })
  }

  const eyeToggle = (
    <button
      type="button"
      onClick={() => setShowPassword(!showPassword)}
      className="absolute right-0 top-2 text-muted-foreground transition-colors hover:text-primary"
      aria-label={showPassword ? 'Şifreyi gizle' : 'Şifreyi göster'}
    >
      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
    </button>
  )

  return (
    <div className="flex min-h-screen w-full overflow-hidden bg-background lg:h-screen">
      {/* Sol yarı — fotoğraf vitrin paneli (Booking/Airbnb dili). Register modunda sağa
          kayar (kartla yer değiştirir); yalnızca lg+; mobilde gizli, kart tam genişlik olur.
          Fotoğrafın üstünde beyaz metin (tema-bağımsız) + dolu beyaz bilgi kartları — cam yok. */}
      <div
        className={cn(
          'relative hidden overflow-hidden transition-transform duration-700 ease-in-out lg:flex lg:w-1/2 lg:flex-col lg:justify-between',
          isRegister ? 'lg:translate-x-full' : 'lg:translate-x-0',
        )}
      >
        <img
          src={isRegister ? hotelImage : heroImage}
          alt={
            isRegister
              ? 'Okyanus manzaralı lüks otel süiti'
              : 'Bulutların üzerinde uçak kanadı ve tropik kıyı şeridi'
          }
          className="absolute inset-0 h-full w-full object-cover"
        />
        {/* Okunabilirlik için koyu dikey geçiş (fotoğraf scrim'i — dekoratif gradyan değil). */}
        <div className="absolute inset-0 bg-gradient-to-b from-brand-navy/80 via-brand-navy/40 to-brand-navy/85" />

        {/* Üst marka satırı */}
        <div className="relative z-10 flex items-center justify-between p-10">
          <span className="text-sm font-medium uppercase tracking-widest text-white/80">
            Seyahatini Keşfet
          </span>
          <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-900">
            2026 Sezonu
          </span>
        </div>

        {/* Orta başlık */}
        <div className="relative z-10 px-10">
          <h2 className="max-w-md text-balance text-4xl font-bold leading-tight text-white">
            Uçuşlar ve oteller, tek bir akıllı asistanda.
          </h2>
          <p className="mt-4 max-w-sm text-pretty text-sm leading-relaxed text-white/80">
            En iyi fiyatları yakala, yolculuğunu planla ve dünyanın her yerinde kendini evinde
            hisset.
          </p>
        </div>

        {/* Yüzen kartlar — dolu beyaz, koyu yazı (fotoğrafın üstünde crisp okunur). */}
        <div className="relative z-10 space-y-4 p-10">
          {/* Uçuş kartı */}
          <div className="rounded-2xl bg-white p-5 shadow-soft-lg">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
                <Plane className="h-4 w-4" />
                Uçuş · TK1980
              </div>
              <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
                %32 indirim
              </span>
            </div>
            <div className="mt-4 flex items-center justify-between">
              <div>
                <p className="text-2xl font-bold text-slate-900">IST</p>
                <p className="text-xs text-slate-500">09:40</p>
              </div>
              <div className="flex flex-1 flex-col items-center px-4">
                <div className="flex w-full items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-300" />
                  <span className="h-px flex-1 bg-slate-200" />
                  <Plane className="h-3.5 w-3.5 text-slate-400" />
                  <span className="h-px flex-1 bg-slate-200" />
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-300" />
                </div>
                <p className="mt-1 text-[11px] text-slate-400">3s 25dk</p>
              </div>
              <div className="text-right">
                <p className="text-2xl font-bold text-slate-900">CDG</p>
                <p className="text-xs text-slate-500">12:05</p>
              </div>
            </div>
          </div>

          {/* Otel kartı */}
          <div className="flex items-center gap-4 rounded-2xl bg-white p-4 shadow-soft-lg">
            <img
              src={hotelImage}
              alt="Okyanus manzaralı lüks otel süiti"
              className="h-16 w-16 flex-shrink-0 rounded-xl object-cover"
            />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1 text-xs text-slate-500">
                <MapPin className="h-3 w-3" />
                Paris, Fransa
              </div>
              <p className="mt-0.5 truncate text-sm font-semibold text-slate-900">Le Marais Suites</p>
              <div className="mt-1 flex items-center gap-3 text-xs text-slate-500">
                <span className="flex items-center gap-1">
                  <Star className="h-3 w-3 fill-warning text-warning" />
                  4.9
                </span>
                <span className="flex items-center gap-1">
                  <Wifi className="h-3 w-3" />
                  Ücretsiz
                </span>
              </div>
            </div>
            {/* Dekoratif vitrin öğesi — işlevi yok, klavye/ekran okuyucuya kapalı. */}
            <span
              aria-hidden="true"
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-brand-orange text-brand-navy transition-transform hover:scale-105"
            >
              <ArrowRight className="h-4 w-4" />
            </span>
          </div>
        </div>
      </div>

      {/* Sağ yarı — giriş/kayıt kartı (düz beyaz yüzey). Register modunda sola kayar. */}
      <div
        className={cn(
          'relative flex w-full items-center justify-center overflow-hidden px-4 py-8 transition-transform duration-700 ease-in-out lg:w-1/2',
          isRegister ? 'lg:-translate-x-full' : 'lg:translate-x-0',
        )}
      >
        <div className="relative z-10 w-full max-w-md rounded-3xl border border-border bg-card p-8 shadow-soft-lg">
          <div className="mb-6 flex justify-center">
            <Logo height={44} />
          </div>

          {/* Mod'a göre değişen içerik — geçişte fade animasyonu. */}
          <div key={mode} className="auth-panel-anim">
            <div className="mb-8 space-y-1.5 text-center">
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                {isRegister ? 'Hesap oluştur' : 'Tekrar hoş geldin'}
              </h1>
              <p className="text-sm text-muted-foreground">
                {isRegister
                  ? 'Birkaç saniyede ücretsiz hesabını aç.'
                  : 'Seyahat asistanına giriş yap.'}
              </p>
            </div>

            {isRegister ? (
              <form onSubmit={handleRegister} className="space-y-8">
                <FloatingInput
                  id="reg_fullname"
                  label="Ad Soyad"
                  value={fullName}
                  onChange={setFullName}
                />
                <FloatingInput
                  id="reg_email"
                  label="E-posta"
                  type="email"
                  value={email}
                  onChange={setEmail}
                />
                <FloatingInput
                  id="reg_password"
                  label="Şifre"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={setPassword}
                  trailing={eyeToggle}
                />
                <FloatingInput
                  id="reg_confirm"
                  label="Şifre (Tekrar)"
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={setConfirmPassword}
                />

                {shownError && (
                  // Sayfa artık AÇIK yüzeyde (bg-card): tema-duyarlı okunur hata rengi.
                  <p role="alert" className="text-xs text-destructive-emphasis">
                    {shownError}
                  </p>
                )}

                <Button type="submit" variant="cta" size="xl" disabled={submitting} className="w-full">
                  {submitting ? 'Kayıt yapılıyor…' : 'Kayıt Ol'}
                </Button>
              </form>
            ) : (
              <form onSubmit={handleLogin} className="space-y-8">
                <FloatingInput
                  id="login_email"
                  label="E-posta"
                  type="email"
                  value={email}
                  onChange={setEmail}
                />
                <FloatingInput
                  id="login_password"
                  label="Şifre"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={setPassword}
                  trailing={eyeToggle}
                />

                <div className="flex items-center justify-end">
                  <button
                    type="button"
                    onClick={onForgotPassword}
                    className="text-xs text-muted-foreground transition hover:text-foreground"
                  >
                    Şifremi unuttum?
                  </button>
                </div>

                {shownError && (
                  <p role="alert" className="text-xs text-destructive-emphasis">
                    {shownError}
                  </p>
                )}

                <Button type="submit" variant="cta" size="xl" disabled={submitting} className="w-full">
                  {submitting ? 'Giriş yapılıyor…' : 'Giriş Yap'}
                </Button>

                <Button
                  type="button"
                  variant="secondary"
                  size="xl"
                  onClick={onGuestContinue}
                  className="w-full"
                >
                  Misafir olarak devam et
                </Button>
              </form>
            )}

            <p className="mt-6 text-center text-xs text-muted-foreground">
              {isRegister ? 'Zaten hesabın var mı? ' : 'Hesabın yok mu? '}
              <button
                type="button"
                onClick={() => switchMode(isRegister ? 'login' : 'register')}
                className="font-semibold text-primary transition hover:underline"
              >
                {isRegister ? 'Giriş yap' : 'Kayıt ol'}
              </button>
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * Kimlik doğrulama akışını backend'e bağlar (docs/frontend-architecture.md §3, §5):
 * giriş / kayıt → POST /api/v1/auth/login|register; dönen { user, token }
 * Redux'a yazılır (authSlice jetonu Axios'a ve localStorage'a aynalar) ve
 * /chat'e yönlendirilir. Misafir jetonsuz yerel oturumdur — backend'e gitmez.
 * Şifre burada saklanmaz; doğrulama tamamen backend'dedir.
 */
export default function LoginPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAppSelector((s) => s.auth.user)
  const [submitting, setSubmitting] = useState(false)
  const [apiError, setApiError] = useState('')
  const [forgotOpen, setForgotOpen] = useState(false)

  // RequireAccount bir misafiri buraya yönlendirdiyse nedenini + geldiği sayfayı taşır.
  const redirectState = location.state as { reason?: string; from?: string } | null
  const accountRequired = redirectState?.reason === 'account-required'

  useEffect(() => {
    if (accountRequired) {
      toast.info('Rezervasyon için giriş yapın veya ücretsiz bir hesap oluşturun.')
    }
  }, [accountRequired])

  // Yalnızca GERÇEK (misafir olmayan) oturum açıkken uygulamaya geri dön. Misafir /login'de
  // kalabilir ki giriş/kayıt ile hesaba yükselsin (bounce edilirse formu hiç göremezdi).
  if (user && !user.guest) return <Navigate to="/chat" replace />

  // Giriş/kayıt başarısında misafirin gelmek istediği korumalı sayfaya (varsa) dön; yoksa /chat.
  const goToApp = () => navigate(redirectState?.from ?? '/chat', { replace: true })

  const runAuth = async (call: () => Promise<AuthResponse>) => {
    if (submitting) return
    setSubmitting(true)
    setApiError('')
    try {
      const response = await call()
      dispatch(sessionStarted(response))
      goToApp()
    } catch (err) {
      setApiError((err as ApiError).message || 'İşlem başarısız — lütfen tekrar deneyin.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <AuthScreen
        submitting={submitting}
        errorMessage={apiError}
        onModeSwitch={() => setApiError('')}
        onLogin={(email, password) => {
          void runAuth(() => authApi.login({ email, password }))
        }}
        onRegister={({ email, fullName, password }) => {
          void runAuth(() => authApi.register({ email, password, name: fullName || undefined }))
        }}
        onGuestContinue={() => {
          dispatch(guestSessionStarted())
          // Misafir korumalı sayfaya giremez; her zaman sohbete gönder ('from'u kullanma).
          navigate('/chat', { replace: true })
        }}
        onForgotPassword={() => setForgotOpen(true)}
      />
      <ForgotPasswordModal open={forgotOpen} onClose={() => setForgotOpen(false)} />
    </>
  )
}
