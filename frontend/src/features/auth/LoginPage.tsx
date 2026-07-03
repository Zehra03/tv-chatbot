import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { ArrowRight, Eye, EyeOff, MapPin, Plane, Star, Wifi } from 'lucide-react'
import heroImage from '@/assets/travel-hero.png'
import hotelImage from '@/assets/hotel-room.png'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { login } from '@/features/auth/authSlice'
import { cn } from '@/lib/utils'

type AuthMode = 'login' | 'register'

interface RegisterData {
  fullName: string
  email: string
  password: string
}

interface AuthScreenProps {
  logoSrc?: string
  onLogin?: (email: string, password: string) => void
  onRegister?: (data: RegisterData) => void
  onGuestContinue?: () => void
  onForgotPassword?: () => void
}

/** Shared floating-label input — keeps the login/register fields visually identical. */
interface FloatingInputProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  trailing?: React.ReactNode
}

function FloatingInput({ id, label, value, onChange, type = 'text', trailing }: FloatingInputProps) {
  return (
    <div className="relative z-0 form-group-animated">
      <input
        type={type}
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`block py-2.5 px-0 w-full text-sm text-white bg-transparent border-0 border-b-2 border-brand-ice/30 appearance-none focus:outline-none focus:ring-0 focus:border-brand-teal peer${trailing ? ' pr-8' : ''}`}
        placeholder=" "
        required
      />
      <label
        htmlFor={id}
        className="absolute text-xs text-brand-ice/60 duration-300 transform -translate-y-6 scale-75 top-3 -z-10 origin-[0] peer-focus:left-0 peer-focus:text-brand-teal peer-placeholder-shown:scale-100 peer-placeholder-shown:translate-y-0 peer-focus:scale-75 peer-focus:-translate-y-6 uppercase tracking-wider font-mono"
      >
        {label}
      </label>
      <div className="input-glow-bar"></div>
      {trailing}
    </div>
  )
}

const AuthScreen: React.FC<AuthScreenProps> = ({
  logoSrc = '/logo.png',
  onLogin = () => {},
  onRegister = () => {},
  onGuestContinue = () => {},
  onForgotPassword = () => {},
}) => {
  const [mode, setMode] = useState<AuthMode>('login')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')

  const isRegister = mode === 'register'

  const switchMode = (next: AuthMode) => {
    setError('')
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
    setError('')
    onRegister({ fullName, email, password })
  }

  // Static randomized blob field (computed once).
  const blobsData = useMemo(
    () =>
      Array.from({ length: 8 }).map(() => ({
        size: Math.random() * 250 + 200,
        left: Math.random() * 80 + 10,
        top: Math.random() * 80 + 10,
        animationDelay: Math.random() * -20,
        animationDuration: Math.random() * 20 + 15,
      })),
    [],
  )

  const blobRefs = useRef<(HTMLDivElement | null)[]>([])

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      const x = e.clientX / window.innerWidth
      const y = e.clientY / window.innerHeight
      blobRefs.current.forEach((blob, index) => {
        if (blob) {
          const speed = (index + 1) * 15
          blob.style.marginLeft = `${x * speed}px`
          blob.style.marginTop = `${y * speed}px`
        }
      })
    }
    document.addEventListener('mousemove', handleMouseMove)
    return () => document.removeEventListener('mousemove', handleMouseMove)
  }, [])

  const eyeToggle = (
    <button
      type="button"
      onClick={() => setShowPassword(!showPassword)}
      className="absolute right-0 top-2 text-brand-ice/50 hover:text-brand-teal transition-colors"
      aria-label={showPassword ? 'Şifreyi gizle' : 'Şifreyi göster'}
    >
      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
    </button>
  )

  return (
    <div className="min-h-screen lg:h-screen w-full flex overflow-hidden bg-brand-navy">
      <style>{`
        @keyframes mercuryFloat {
          0% { transform: translate(0, 0) scale(1); }
          33% { transform: translate(10vw, 20vh) scale(1.2); }
          66% { transform: translate(-5vw, 10vh) scale(0.8); }
          100% { transform: translate(5vw, -10vh) scale(1.1); }
        }

        .mercury-blob {
          position: absolute;
          border-radius: 50%;
          filter: blur(40px);
          animation: mercuryFloat 20s infinite alternate ease-in-out;
          transition: margin 0.1s ease-out;
          opacity: 0.3;
        }

        .gooey-filter {
          filter: url(#gooey);
        }

        .form-group-animated {
          transition: transform 0.4s cubic-bezier(0.2, 1, 0.3, 1);
        }

        .form-group-animated:focus-within {
          transform: translateX(8px);
        }

        .input-glow-bar {
          position: absolute;
          bottom: 0;
          left: 0;
          width: 0%;
          height: 2px;
          background: linear-gradient(90deg, #17D6C3, #2E8FFF);
          transition: width 0.6s cubic-bezier(0.2, 1, 0.3, 1);
          box-shadow: 0 0 15px #17D6C3;
        }

        .form-group-animated:focus-within .input-glow-bar {
          width: 100%;
        }

        /* Sağ kart içeriği mod değişince yumuşakça belirir. */
        @keyframes authPanelFade {
          from { opacity: 0; transform: translateY(10px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .auth-panel-anim {
          animation: authPanelFade 0.5s cubic-bezier(0.2, 1, 0.3, 1);
        }
      `}</style>

      <svg className="absolute w-0 h-0">
        <defs>
          <filter id="gooey">
            <feGaussianBlur in="SourceGraphic" stdDeviation="15" result="blur" />
            <feColorMatrix
              in="blur"
              mode="matrix"
              values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 20 -10"
              result="goo"
            />
            <feComposite in="SourceGraphic" in2="goo" operator="atop" />
          </filter>
        </defs>
      </svg>

      {/* Sol yarı — vitrin paneli. Register modunda sağa kayar (kartla yer değiştirir);
          yalnızca lg+; mobilde gizli, kart tam genişlik olur. */}
      <div
        className={cn(
          'hidden lg:flex lg:w-1/2 lg:flex-col lg:justify-between relative overflow-hidden transition-transform duration-700 ease-in-out',
          isRegister ? 'lg:translate-x-full' : 'lg:translate-x-0',
        )}
      >
        {/* Görsel register modunda otel görseline döner. */}
        <img
          src={isRegister ? hotelImage : heroImage}
          alt={
            isRegister
              ? 'Okyanus manzaralı lüks otel süiti'
              : 'Bulutların üzerinde uçak kanadı ve tropik kıyı şeridi'
          }
          className="absolute inset-0 h-full w-full object-cover"
        />
        {/* Okunabilirlik için koyu dikey geçiş. */}
        <div className="absolute inset-0 bg-gradient-to-b from-brand-navy/80 via-brand-navy/40 to-brand-navy/85" />

        {/* Üst marka satırı */}
        <div className="relative z-10 flex items-center justify-between p-10">
          <span className="text-sm font-medium tracking-widest text-brand-ice/80 uppercase">
            Seyahatini Keşfet
          </span>
          <span className="rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-white backdrop-blur">
            2026 Sezonu
          </span>
        </div>

        {/* Orta başlık */}
        <div className="relative z-10 px-10">
          <h2 className="max-w-md text-4xl font-bold leading-tight text-balance text-white">
            Uçuşlar ve oteller, tek bir akıllı asistanda.
          </h2>
          <p className="mt-4 max-w-sm text-pretty text-sm leading-relaxed text-brand-ice/80">
            En iyi fiyatları yakala, yolculuğunu planla ve dünyanın her yerinde kendini evinde
            hisset.
          </p>
        </div>

        {/* Yüzen kartlar */}
        <div className="relative z-10 space-y-4 p-10">
          {/* Uçuş kartı */}
          <div className="rounded-2xl border border-white/15 bg-white/10 p-5 backdrop-blur-md">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs font-medium text-white/70">
                <Plane className="h-4 w-4" />
                Uçuş · TK1980
              </div>
              <span className="rounded-full bg-brand-teal/25 px-2.5 py-0.5 text-xs font-semibold text-white">
                %32 indirim
              </span>
            </div>
            <div className="mt-4 flex items-center justify-between">
              <div>
                <p className="text-2xl font-bold text-white">IST</p>
                <p className="text-xs text-white/70">09:40</p>
              </div>
              <div className="flex flex-1 flex-col items-center px-4">
                <div className="flex w-full items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
                  <span className="h-px flex-1 bg-white/40" />
                  <Plane className="h-3.5 w-3.5 text-white/70" />
                  <span className="h-px flex-1 bg-white/40" />
                  <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
                </div>
                <p className="mt-1 text-[11px] text-white/60">3s 25dk</p>
              </div>
              <div className="text-right">
                <p className="text-2xl font-bold text-white">CDG</p>
                <p className="text-xs text-white/70">12:05</p>
              </div>
            </div>
          </div>

          {/* Otel kartı */}
          <div className="flex items-center gap-4 rounded-2xl border border-white/15 bg-white/10 p-4 backdrop-blur-md">
            <img
              src={hotelImage}
              alt="Okyanus manzaralı lüks otel süiti"
              className="h-16 w-16 flex-shrink-0 rounded-xl object-cover"
            />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1 text-xs text-white/70">
                <MapPin className="h-3 w-3" />
                Paris, Fransa
              </div>
              <p className="mt-0.5 truncate text-sm font-semibold text-white">Le Marais Suites</p>
              <div className="mt-1 flex items-center gap-3 text-xs text-white/70">
                <span className="flex items-center gap-1">
                  <Star className="h-3 w-3 fill-brand-teal text-brand-teal" />
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
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-white text-brand-navy transition-transform hover:scale-105"
            >
              <ArrowRight className="h-4 w-4" />
            </span>
          </div>
        </div>
      </div>

      {/* Sağ yarı — giriş/kayıt kartı, arkasında animasyonlu "mercury blob" alanı.
          Register modunda sola kayar (görselle yer değiştirir). */}
      <div
        className={cn(
          'relative w-full lg:w-1/2 flex items-center justify-center px-4 py-8 overflow-hidden transition-transform duration-700 ease-in-out',
          isRegister ? 'lg:-translate-x-full' : 'lg:translate-x-0',
        )}
      >
        <div className="absolute inset-0 gooey-filter">
          {blobsData.map((data, index) => (
            <div
              key={index}
              ref={(el) => {
                blobRefs.current[index] = el
              }}
              className="mercury-blob"
              style={{
                width: `${data.size}px`,
                height: `${data.size}px`,
                left: `${data.left}%`,
                top: `${data.top}%`,
                animationDelay: `${data.animationDelay}s`,
                animationDuration: `${data.animationDuration}s`,
                background:
                  index % 3 === 0
                    ? 'linear-gradient(135deg, #2E8FFF, #8B8CFF)'
                    : index % 3 === 1
                      ? 'linear-gradient(135deg, #17D6C3, #A9E9FF)'
                      : 'linear-gradient(135deg, #8B8CFF, #2E8FFF)',
                boxShadow:
                  'inset -10px -10px 30px rgba(0,0,0,0.3), 10px 10px 40px rgba(46, 143, 255, 0.2)',
              }}
            />
          ))}
        </div>

        <div className="w-full max-w-md p-8 bg-white/10 backdrop-blur-lg rounded-3xl border border-white/20 shadow-2xl relative z-10">
          <div className="flex justify-center mb-6">
            <div className="relative">
              {/* Arka ışıltı — logoyu koyu kartta daha görünür kılar. */}
              <div className="absolute inset-0 -m-6 rounded-full bg-gradient-to-r from-brand-iris via-brand-blue to-brand-teal blur-3xl opacity-30"></div>
              <div className="absolute inset-0 -m-2 rounded-[2rem] bg-white/25 blur-2xl"></div>
              <img
                src={logoSrc}
                alt="PaxAssist Logo"
                className="relative z-10 h-40 lg:h-60 w-auto object-contain"
                onError={(e) => {
                  const target = e.target as HTMLImageElement
                  target.style.display = 'none'
                }}
              />
            </div>
          </div>

          {/* Mod'a göre değişen içerik — geçişte fade animasyonu. */}
          <div key={mode} className="auth-panel-anim">
            <div className="space-y-2 text-center mb-8">
              <h1 className="text-3xl font-bold tracking-tight bg-gradient-to-r from-brand-ice via-brand-teal to-brand-iris bg-clip-text text-transparent">
                {isRegister ? 'Hesap oluştur' : 'Tekrar hoş geldin'}
              </h1>
              <p className="text-sm text-brand-ice/80">
                {isRegister
                  ? 'Aramıza katıl, seyahatini planlamaya başla.'
                  : 'Devam etmek için giriş yap.'}
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

                {error && (
                  <p role="alert" className="text-xs text-destructive">
                    {error}
                  </p>
                )}

                <div className="relative gooey-filter mt-12">
                  <div className="absolute top-1/2 left-1/2 w-full h-full bg-gradient-to-r from-brand-blue to-brand-teal transform -translate-x-1/2 -translate-y-1/2 rounded-full transition-all duration-500 hover:scale-105 opacity-60"></div>
                  <button
                    type="submit"
                    className="relative z-10 w-full py-4 px-4 bg-white text-brand-navy font-bold tracking-widest uppercase text-sm hover:tracking-[0.3em] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal transition-all duration-300"
                  >
                    Kayıt Ol
                  </button>
                </div>
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
                    className="text-xs text-brand-ice/70 hover:text-white transition"
                  >
                    Şifremi unuttum?
                  </button>
                </div>

                <div className="relative gooey-filter mt-12">
                  <div className="absolute top-1/2 left-1/2 w-full h-full bg-gradient-to-r from-brand-blue to-brand-teal transform -translate-x-1/2 -translate-y-1/2 rounded-full transition-all duration-500 hover:scale-105 opacity-60"></div>
                  <button
                    type="submit"
                    className="relative z-10 w-full py-4 px-4 bg-white text-brand-navy font-bold tracking-widest uppercase text-sm hover:tracking-[0.3em] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal transition-all duration-300"
                  >
                    Giriş Yap
                  </button>
                </div>

                <button
                  type="button"
                  onClick={onGuestContinue}
                  className="w-full flex items-center justify-center py-3 px-4 bg-white/5 hover:bg-white/10 rounded-xl text-brand-ice font-semibold border border-brand-ice/30 hover:border-brand-teal focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal transition-all duration-300"
                >
                  Misafir olarak devam et
                </button>
              </form>
            )}

            <p className="text-center text-xs text-brand-ice/70 mt-6">
              {isRegister ? 'Zaten hesabın var mı? ' : 'Hesabın yok mu? '}
              <button
                type="button"
                onClick={() => switchMode(isRegister ? 'login' : 'register')}
                className="font-semibold bg-gradient-to-r from-brand-teal to-brand-iris bg-clip-text text-transparent hover:from-brand-teal/80 hover:to-brand-iris/80 transition"
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
 * Mock kimlik doğrulama akışını bağlar (docs/frontend-architecture.md §3, §5):
 * giriş / kayıt / misafir → kullanıcıyı Redux'a yazar ve /chat'e yönlendirir.
 * Zaten oturum açıksa doğrudan /chat'e gider. Gerçek doğrulama backend'de;
 * şifre burada doğrulanmaz (mock) ve hiçbir sır saklanmaz.
 */
export default function LoginPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const user = useAppSelector((s) => s.auth.user)

  // Oturum açıkken /login'e gelinirse uygulamaya geri dön.
  if (user) return <Navigate to="/chat" replace />

  const goToApp = () => navigate('/chat', { replace: true })

  return (
    <AuthScreen
      onLogin={(email) => {
        dispatch(login({ email }))
        goToApp()
      }}
      onRegister={({ email, fullName }) => {
        dispatch(login({ email, name: fullName }))
        goToApp()
      }}
      onGuestContinue={() => {
        dispatch(login({ email: '', name: 'Misafir', guest: true }))
        goToApp()
      }}
    />
  )
}
