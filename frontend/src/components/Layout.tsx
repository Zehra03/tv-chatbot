import { useEffect, useState } from 'react'
import { NavLink, useLocation, useMatches, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Menu, UserRound, X } from 'lucide-react'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { GooeyNav } from '@/components/GooeyNav'
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

  // Chat, viewport'a oturan bir "uygulama" görünümü: geniş dikey oluk (py-8)
  // yüksekliği çalar ve panel/thread'i fold'un altına itebilir. Bu rotada oluğu
  // kısarak tüm chat + geçmiş sayfayı kaydırmadan görünür tutarız; diğer
  // (kayan) sayfalar rahat py-8'i korur.
  const isChat = location.pathname.startsWith('/chat')

  // GooeyNav'ın aktif öğesi rotadan türer; '/reservation/new' da Rezervasyonlar
  // sekmesini işaretler. Eşleşme yoksa (-1, ör. /profile) pill gizlenir.
  const activeNavIndex = NAV.findIndex((item) =>
    item.to === '/reservations'
      ? location.pathname.startsWith('/reservation')
      : location.pathname.startsWith(item.to),
  )

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login', { replace: true })
  }

  // ——— GEÇİCİ TEŞHİS — kaydırma sorununu ölçer, sonra kaldırılacak. ———
  const [dbg, setDbg] = useState('ölçülüyor…')
  useEffect(() => {
    const measure = () => {
      const mainEl = document.querySelector('main') as HTMLElement | null
      const sidebars = document.querySelectorAll('[aria-label="Sohbet geçmişi"]')
      const log = document.querySelector('[role="log"]') as HTMLElement | null
      const h = (el: Element | null) =>
        el ? Math.round(el.getBoundingClientRect().height) : -1
      // main içindeki, KIRPILMAYAN (arada overflow-hidden/auto yok) en uzun eleman.
      let worst: { cls: string; h: number } | null = null
      if (mainEl) {
        mainEl.querySelectorAll('*').forEach((el) => {
          const r = (el as HTMLElement).getBoundingClientRect()
          if (r.height > (worst?.h ?? 800) && r.height > 800) {
            worst = {
              cls: (el.className?.toString?.() ?? '').slice(0, 45) || el.tagName,
              h: Math.round(r.height),
            }
          }
        })
      }
      setDbg(
        [
          `vh=${window.innerHeight}`,
          `main=${h(mainEl)} sc=${mainEl?.scrollHeight ?? -1}`,
          `#sidebar=${sidebars.length} #log=${document.querySelectorAll('[role="log"]').length}`,
          `log h=${h(log)} sc=${log?.scrollHeight ?? -1}(${log ? getComputedStyle(log).overflowY : '?'})`,
          worst ? `TALL[${(worst as { cls: string; h: number }).h}]=${(worst as { cls: string; h: number }).cls}` : 'TALL=none',
        ].join(' | '),
      )
    }
    measure()
    const id = window.setInterval(measure, 400)
    window.addEventListener('resize', measure)
    return () => {
      window.clearInterval(id)
      window.removeEventListener('resize', measure)
    }
  }, [])
  // ——— /GEÇİCİ TEŞHİS ———

  return (
    <div
      className={cn(
        // Viewport'a kilitli kabuk: gövde ASLA kaymaz (overflow-hidden), yalnızca
        // <main> kayar. Böylece fixed gece-uçuşu arka planı, içerik bittikten sonra
        // "boş kaydırılabilir alan" olarak sızamaz. h-[100dvh] mobilde dinamik
        // araç çubuğuna da uyar; header + main flex sütununu paylaşır.
        'flex h-screen flex-col overflow-hidden transition-colors duration-700 supports-[height:100dvh]:h-[100dvh]',
        // 'dark' sınıfı semantik token'ları (bg-card, muted-foreground, border…)
        // koyu palete çevirir — token'la yazılmış sayfalar otomatik okunur kalır.
        dark ? 'dark bg-brand-navy text-white' : 'bg-background text-foreground',
      )}
    >
      {/* GEÇİCİ TEŞHİS katmanı — ölçümü göster, sonra kaldırılacak. */}
      <div
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          zIndex: 99999,
          background: 'rgba(0,0,0,0.85)',
          color: '#7CFF7C',
          font: '12px monospace',
          padding: '3px 8px',
          textAlign: 'center',
          pointerEvents: 'none',
        }}
      >
        {dbg}
      </div>

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
          // Kabuğun sabit üst satırı — flex sütununda küçülmez (shrink-0); sticky
          // yerine gerçek akış elemanı, çünkü main artık tek kaydırma kabı.
          'relative z-40 shrink-0 border-b backdrop-blur-md transition-colors duration-700',
          dark ? 'border-white/10 bg-brand-navy/70' : 'border-border bg-background/80',
        )}
      >
        {/* Bar tam genişlik (container sınırı yok) — h-24; ChatPage 10rem hesabı buna bağlı. */}
        <div className="relative flex h-24 w-full items-center justify-between gap-4 px-4 sm:px-8">
          <NavLink to="/" aria-label="Ana sayfa" onClick={() => setMenuOpen(false)}>
            {/* Koyu yüzeyde login'deki halo hilesi: lacivert harfler okunur kalır. */}
            <span className="relative inline-block">
              {dark && (
                <span
                  aria-hidden="true"
                  className="absolute inset-0 -m-1 rounded-full bg-white/35 blur-md"
                />
              )}
              <Logo height={88} className="relative" />
            </span>
          </NavLink>
          {/* Masaüstü navigasyonu — barın gerçek ortasında (mutlak konum, logo ve
              sağ eylemlerden bağımsız); mobilde gizli, hamburger paneline taşınır. */}
          <div className="absolute left-1/2 top-1/2 hidden -translate-x-1/2 -translate-y-1/2 md:block">
            <GooeyNav
              items={NAV.map((item) => ({ label: item.label, href: item.to }))}
              activeIndex={activeNavIndex}
              onNavigate={(href) => navigate(href)}
            />
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

        {/* Mobil panel — overlay olarak açılır (header shrink-0 yüksekliği
            sabit kalır, kabuk düzeni bozulmaz); linke tıklayınca kapanır. */}
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
                  className="shrink-0"
                  onClick={handleLogout}
                >
                  Çıkış
                </Button>
              </div>
            </nav>
          </div>
        )}
      </header>

      {/* İçerik kabı (flex-1). Chat = TEK SAYFA: main kaydırılamaz (overflow-hidden),
          böylece sayfa hiç kaymaz — geçmiş paneli ve mesaj thread'i yalnızca kendi
          içlerinde kayar. Diğer (sonuç/form) sayfaları uzun olabilir; onlarda main
          tek kaydırma kabı olarak kayar, header sabit kalır, arka plan sızmaz.
          İç sarmalayıcı header'ın px-4/sm:px-8 oluklarıyla hizalı, max-w-7xl sınırlı. */}
      <main
        className={cn(
          'relative z-10 flex min-h-0 flex-1 flex-col',
          isChat ? 'overflow-hidden' : 'overflow-y-auto',
        )}
      >
        <div
          className={cn(
            'mx-auto flex w-full min-h-0 max-w-7xl flex-1 flex-col px-4 sm:px-8',
            isChat ? 'py-4' : 'py-8',
          )}
        >
          {/* Rota geçişi: mode="wait" + pathname key — AnimatedOutlet ayrılan
              kopyada eski sayfayı dondurur (frozen outlet deseni). flex-1 +
              min-h-0: chat kabı dikeyde oturur, sonuç sayfaları taşıp main'i kaydırır. */}
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={location.pathname}
              className="flex min-h-0 flex-1 flex-col"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22, ease: 'easeOut' }}
            >
              <AnimatedOutlet />
            </motion.div>
          </AnimatePresence>
        </div>
      </main>
    </div>
  )
}
