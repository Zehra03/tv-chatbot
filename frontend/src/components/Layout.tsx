import { useState } from 'react'
import { NavLink, useLocation, useMatches, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Menu, UserRound, X } from 'lucide-react'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { Logo } from '@/components/Logo'
import { NightSkyBackground } from '@/components/NightSkyBackground'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { zoneFromMatches, type Zone } from '@/app/zones'
import { logout } from '@/features/auth/authSlice'
import { cn } from '@/lib/utils'

/** Korumalı sayfaların ortak çatısı: üst bar (logo + navigasyon + kullanıcı/çıkış)
 * ve içerik alanı. İçerik <AnimatedOutlet /> ile buraya render edilir.
 * Responsive: md+ satır içi navigasyon; mobilde hamburger ile açılan panel.
 *
 * Bölge mekanizması: rotanın `handle.zone` işaretine göre yüzey koyu (ai) ya da
 * açık (controlled) boyanır; 700ms renk geçişi "bu adımda AI devre dışı"
 * anlatısının görsel karşılığıdır. */
const NAV = [
  { to: '/chat', label: 'Sohbet' },
  { to: '/hotels', label: 'Oteller' },
  { to: '/flights', label: 'Uçuşlar' },
  { to: '/reservations', label: 'Rezervasyonlar' },
]

/** Masaüstü nav: açık bölgede pill, koyu bölgede teal glow alt çizgi. */
const desktopNavLinkClass =
  (zone: Zone) =>
  ({ isActive }: { isActive: boolean }) =>
    zone === 'ai'
      ? cn(
          'relative rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive
            ? 'text-white after:absolute after:inset-x-3 after:-bottom-0.5 after:h-0.5 after:rounded-full after:bg-brand-teal after:shadow-[0_0_8px_theme(colors.brand.teal)]'
            : 'text-brand-ice/60 hover:text-white',
        )
      : cn(
          'rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive
            ? 'bg-accent text-accent-foreground'
            : 'text-muted-foreground hover:text-foreground',
        )

/** Mobil panel nav: her iki bölgede de pill (dikey listede alt çizgi okunmaz). */
const mobileNavLinkClass =
  (zone: Zone) =>
  ({ isActive }: { isActive: boolean }) =>
    zone === 'ai'
      ? cn(
          'rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive ? 'bg-white/10 text-white' : 'text-brand-ice/60 hover:text-white',
        )
      : cn(
          'rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive
            ? 'bg-accent text-accent-foreground'
            : 'text-muted-foreground hover:text-foreground',
        )

export function Layout() {
  const user = useAppSelector((s) => s.auth.user)
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const matches = useMatches()
  const [menuOpen, setMenuOpen] = useState(false)

  const zone = zoneFromMatches(matches)
  const dark = zone === 'ai'

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login', { replace: true })
  }

  return (
    <div
      className={cn(
        'min-h-screen transition-colors duration-700',
        dark ? 'bg-brand-navy text-white' : 'bg-background text-foreground',
      )}
    >
      {/* Gece uçuşu arka planı — yalnızca AI bölgesinde, yumuşak giriş/çıkışla. */}
      <AnimatePresence>
        {dark && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.7 }}
          >
            <NightSkyBackground />
          </motion.div>
        )}
      </AnimatePresence>

      <header
        className={cn(
          'sticky top-0 z-40 border-b backdrop-blur-md transition-colors duration-700',
          dark ? 'border-white/10 bg-brand-navy/70' : 'border-border bg-background/80',
        )}
      >
        {/* Bar tam genişlik (container sınırı yok) — h-20; ChatPage 9rem hesabı buna bağlı. */}
        <div className="flex h-20 w-full items-center justify-between gap-4 px-4 sm:px-8">
          <div className="flex items-center gap-8">
            <NavLink to="/chat" aria-label="Ana sayfa" onClick={() => setMenuOpen(false)}>
              {/* Koyu yüzeyde login'deki halo hilesi: lacivert harfler okunur kalır. */}
              <span className="relative inline-block">
                {dark && (
                  <span
                    aria-hidden="true"
                    className="absolute inset-0 -m-1 rounded-full bg-white/35 blur-md"
                  />
                )}
                <Logo height={44} className="relative" />
              </span>
            </NavLink>
            {/* Masaüstü navigasyonu — mobilde gizli, hamburger paneline taşınır. */}
            <nav className="hidden items-center gap-1 md:flex">
              {NAV.map((item) => (
                <NavLink key={item.to} to={item.to} className={desktopNavLinkClass(zone)}>
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            {/* Kullanıcı adı → profil sayfası. */}
            {user && (
              <NavLink
                to="/profile"
                className={({ isActive }) =>
                  cn(
                    'hidden max-w-[16rem] items-center gap-1.5 truncate rounded-md px-2 py-1 text-sm transition-colors md:flex',
                    dark
                      ? isActive
                        ? 'text-white'
                        : 'text-brand-ice/70 hover:text-white'
                      : isActive
                        ? 'text-foreground'
                        : 'text-muted-foreground hover:text-foreground',
                  )
                }
              >
                <UserRound className="h-4 w-4 shrink-0" aria-hidden />
                <span className="truncate">{user.name ?? user.email}</span>
              </NavLink>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              className={cn(
                'hidden md:inline-flex',
                dark &&
                  'border-brand-ice/30 bg-white/5 text-brand-ice hover:border-brand-teal hover:bg-white/10 hover:text-white',
              )}
            >
              Çıkış
            </Button>
            {/* Mobil menü düğmesi */}
            <Button
              variant="ghost"
              size="icon"
              className={cn('md:hidden', dark && 'text-brand-ice hover:bg-white/10 hover:text-white')}
              aria-label={menuOpen ? 'Menüyü kapat' : 'Menüyü aç'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((o) => !o)}
            >
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>
          </div>
        </div>

        {/* Mobil panel — overlay olarak açılır (header yüksekliği sabit kalır,
            /chat'in 100vh hesabı bozulmaz); linke tıklayınca kapanır. */}
        {menuOpen && (
          <div
            className={cn(
              'absolute inset-x-0 top-full z-50 border-b border-t shadow-md backdrop-blur-md md:hidden',
              dark ? 'border-white/10 bg-brand-navy/95' : 'bg-background',
            )}
          >
            <nav className="flex flex-col gap-1 px-4 py-3 sm:px-8">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={mobileNavLinkClass(zone)}
                  onClick={() => setMenuOpen(false)}
                >
                  {item.label}
                </NavLink>
              ))}
              <div
                className={cn(
                  'mt-2 flex items-center justify-between gap-2 border-t pt-3',
                  dark && 'border-white/10',
                )}
              >
                {/* Kullanıcı adı → profil sayfası (panel kapanır). */}
                {user && (
                  <NavLink
                    to="/profile"
                    onClick={() => setMenuOpen(false)}
                    className={cn(
                      'flex min-w-0 items-center gap-1.5 truncate px-3 text-sm transition-colors',
                      dark
                        ? 'text-brand-ice/70 hover:text-white'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    <UserRound className="h-4 w-4 shrink-0" aria-hidden />
                    <span className="truncate">{user.name ?? user.email}</span>
                  </NavLink>
                )}
                <Button
                  variant="outline"
                  size="sm"
                  className={cn(
                    'shrink-0',
                    dark &&
                      'border-brand-ice/30 bg-white/5 text-brand-ice hover:border-brand-teal hover:bg-white/10 hover:text-white',
                  )}
                  onClick={handleLogout}
                >
                  Çıkış
                </Button>
              </div>
            </nav>
          </div>
        )}
      </header>

      <main className="container relative z-10 py-8">
        {/* Rota geçişi: mode="wait" + pathname key — AnimatedOutlet ayrılan
            kopyada eski sayfayı dondurur (frozen outlet deseni). */}
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
          >
            <AnimatedOutlet />
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  )
}
