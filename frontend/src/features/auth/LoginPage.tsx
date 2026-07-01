import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import heroImage from '@/assets/travel-hero.png'

interface LoginScreenProps {
  logoSrc?: string
  onLogin?: (email: string, password: string) => void
  onGuestContinue?: () => void
  onForgotPassword?: () => void
  onSignUp?: () => void
}

const LoginScreen: React.FC<LoginScreenProps> = ({
  logoSrc = '/logo.png',
  onLogin = () => {},
  onGuestContinue = () => {},
  onForgotPassword = () => {},
  onSignUp = () => {},
}) => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onLogin(email, password)
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

  return (
    <div className="min-h-screen w-full flex bg-brand-navy">
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

      {/* Sol yarı — seyahat görseli (yalnızca lg+; mobilde gizli, kart tam genişlik olur). */}
      <div className="hidden lg:block lg:w-1/2 relative overflow-hidden">
        <img
          src={heroImage}
          alt="PaxAssist ile seyahat"
          className="absolute inset-0 h-full w-full object-cover"
        />
        {/* Görsel kenarını lacivere doğru yumuşatan ince geçiş — sağ yarıyla bütünleşir. */}
        <div className="absolute inset-0 bg-gradient-to-r from-brand-navy/10 to-brand-navy/60" />
      </div>

      {/* Sağ yarı — giriş kartı, arkasında animasyonlu "mercury blob" alanı. */}
      <div className="relative w-full lg:w-1/2 flex items-center justify-center p-4 overflow-hidden">
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

        <div className="w-full max-w-md p-8 space-y-6 bg-white/10 backdrop-blur-lg rounded-3xl border border-white/20 shadow-2xl relative z-10">
          <div className="flex justify-center mb-6 relative">
            <div className="relative">
              <div className="absolute inset-0 bg-gradient-to-r from-brand-iris via-brand-blue to-brand-teal rounded-2xl blur-lg opacity-40 animate-pulse"></div>
              <img
                src={logoSrc}
                alt="PaxAssist Logo"
                className="h-16 object-contain relative z-10"
                onError={(e) => {
                  const target = e.target as HTMLImageElement
                  target.style.display = 'none'
                }}
              />
            </div>
          </div>
          <div className="space-y-2 text-center mb-8">
            <h1 className="text-3xl font-bold tracking-tight bg-gradient-to-r from-brand-ice via-brand-teal to-brand-iris bg-clip-text text-transparent">
              Tekrar hoş geldiniz
            </h1>
            <p className="text-sm text-brand-ice/80">Seyahat asistanınıza giriş yapın</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-8">
            <div className="relative z-0 form-group-animated">
              <input
                type="email"
                id="floating_email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="block py-2.5 px-0 w-full text-sm text-white bg-transparent border-0 border-b-2 border-brand-ice/30 appearance-none focus:outline-none focus:ring-0 focus:border-brand-teal peer"
                placeholder=" "
                required
              />
              <label
                htmlFor="floating_email"
                className="absolute text-xs text-brand-ice/60 duration-300 transform -translate-y-6 scale-75 top-3 -z-10 origin-[0] peer-focus:left-0 peer-focus:text-brand-teal peer-placeholder-shown:scale-100 peer-placeholder-shown:translate-y-0 peer-focus:scale-75 peer-focus:-translate-y-6 uppercase tracking-wider font-mono"
              >
                E-posta
              </label>
              <div className="input-glow-bar"></div>
            </div>

            <div className="relative z-0 form-group-animated">
              <input
                type={showPassword ? 'text' : 'password'}
                id="floating_password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="block py-2.5 px-0 w-full text-sm text-white bg-transparent border-0 border-b-2 border-brand-ice/30 appearance-none focus:outline-none focus:ring-0 focus:border-brand-teal peer pr-8"
                placeholder=" "
                required
              />
              <label
                htmlFor="floating_password"
                className="absolute text-xs text-brand-ice/60 duration-300 transform -translate-y-6 scale-75 top-3 -z-10 origin-[0] peer-focus:left-0 peer-focus:text-brand-teal peer-placeholder-shown:scale-100 peer-placeholder-shown:translate-y-0 peer-focus:scale-75 peer-focus:-translate-y-6 uppercase tracking-wider font-mono"
              >
                Şifre
              </label>
              <div className="input-glow-bar"></div>
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-0 top-2 text-brand-ice/50 hover:text-brand-teal transition-colors"
                aria-label={showPassword ? 'Şifreyi gizle' : 'Şifreyi göster'}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>

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
                className="relative z-10 w-full py-4 px-4 bg-white text-brand-navy font-bold tracking-widest uppercase text-sm hover:tracking-[0.3em] focus:outline-none transition-all duration-300"
              >
                Giriş Yap
              </button>
            </div>

            <button
              type="button"
              onClick={onGuestContinue}
              className="w-full flex items-center justify-center py-3 px-4 bg-white/5 hover:bg-white/10 rounded-xl text-brand-ice font-semibold border border-brand-ice/30 hover:border-brand-teal focus:outline-none transition-all duration-300"
            >
              Misafir olarak devam et
            </button>
          </form>

          <p className="text-center text-xs text-brand-ice/70 mt-6">
            Hesabın yok mu?{' '}
            <button
              type="button"
              onClick={onSignUp}
              className="font-semibold bg-gradient-to-r from-brand-teal to-brand-iris bg-clip-text text-transparent hover:from-brand-teal/80 hover:to-brand-iris/80 transition"
            >
              Kayıt ol
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}

/**
 * Default export wires the screen with mock handlers for the current preview phase.
 * Auth is not yet connected to Redux / React Router (see docs/frontend-architecture.md
 * §3, §5) — swap these for the auth slice + navigation when that lands.
 */
export default function LoginPage() {
  return (
    <LoginScreen
      onLogin={(email, password) => console.log('Login:', email, password)}
      onGuestContinue={() => console.log('Guest continue')}
      onForgotPassword={() => console.log('Forgot password')}
      onSignUp={() => console.log('Sign up')}
    />
  )
}
