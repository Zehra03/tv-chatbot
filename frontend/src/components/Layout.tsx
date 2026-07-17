import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Menu, UserRound, X } from 'lucide-react'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { GooeyNav } from '@/components/GooeyNav'
import { Logo } from '@/components/Logo'
import { NightSkyBackground } from '@/components/NightSkyBackground'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { useTheme } from '@/app/theme'
import { logout } from '@/features/auth/authSlice'
import { cn } from '@/lib/utils'

/** Korumalı sayfaların ortak çatısı: üst bar (logo + navigasyon + kullanıcı/çıkış)
 * ve içerik alanı. İçerik <AnimatedOutlet /> ile buraya render edilir.
 * Responsive: md+ satır içi navigasyon; mobilde hamburger ile açılan panel.
 *
 * Yüzey rengi KULLANICI TEMASINDAN gelir (app/theme.tsx), rotadan değil. Önceden
 * rotanın `handle.zone` işareti boyardı ("AI bölgesi koyu, kontrollü bölge açık");
 * tüm rotalar 'ai' işaretlenip anlatı AiOffBanner'a taşınınca o mekanizma tek
 * renge çökmüştü. Aşağıdaki `dark ? … : …` üçlülerinin açık dalı o tasarımdan
 * kalan sağlam koddur — artık ölü değil, açık temanın ta kendisi. */
const NAV = [
  { to: '/chat', label: 'Sohbet' },
  { to: '/hotels', label: 'Oteller' },
  { to: '/flights', label: 'Uçuşlar' },
  { to: '/reservations', label: 'Rezervasyonlar' },
]

/** Mobil panel nav: her iki temada da pill (dikey listede alt çizgi okunmaz). */
const mobileNavLinkClass =
  (dark: boolean) =>
  ({ isActive }: { isActive: boolean }) =>
    dark
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
  const [menuOpen, setMenuOpen] = useState(false)

  const { resolvedTheme } = useTheme()
  const dark = resolvedTheme === 'dark'

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

  return (
    <div
      className={cn(
        // Viewport'a kilitli kabuk: gövde ASLA kaymaz (overflow-hidden), yalnızca
        // <main> kayar. Böylece fixed gece-uçuşu arka planı, içerik bittikten sonra
        // "boş kaydırılabilir alan" olarak sızamaz. h-[100dvh] mobilde dinamik
        // araç çubuğuna da uyar; header + main flex sütununu paylaşır.
        'flex h-screen flex-col overflow-hidden transition-colors duration-700 supports-[height:100dvh]:h-[100dvh]',
        // 'dark' sınıfı ARTIK BURADA DEĞİL — <html>'e taşındı (app/theme.tsx), çünkü
        // <body>'ye portal olan katmanlar (ui/modal, chat/ResultsPanel, sonner) bu
        // div'in altında değil ve sınıf burada kalsaydı temayı göremezlerdi.
        //
        // Kabuğun boyası (brand-navy/text-white) şimdilik elle duruyor: koyu
        // --background slate, --foreground ise #F8FAFC — token'a geçmek görünümü
        // oynatırdı. Token'lar Gece Uçuşu paletine ayarlanınca burası
        // `bg-background text-foreground`a sadeleşecek.
        dark ? 'bg-brand-navy text-white' : 'bg-background text-foreground',
      )}
    >
      {/* Gece uçuşu arka planı — yalnızca koyu temada, yumuşak giriş/çıkışla.
          Açık temanın "gündüz göğü" karşılığı boyama fazında gelecek; şimdilik
          açık tema düz `bg-background` üstünde durur. */}
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
            {/* Tema seçici — masaüstünde satır içi; mobilde hamburger panelinde. */}
            <ThemeToggle className="hidden md:flex" />
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
                  className={mobileNavLinkClass(dark)}
                  onClick={() => setMenuOpen(false)}
                >
                  {item.label}
                </NavLink>
              ))}
              {/* Tema seçici — mobil panelde kendi satırında. */}
              <div
                className={cn(
                  'mt-2 flex items-center justify-between gap-2 border-t px-3 pt-3',
                  dark && 'border-white/10',
                )}
              >
                <span className={cn('text-sm', dark ? 'text-brand-ice/70' : 'text-muted-foreground')}>
                  Tema
                </span>
                <ThemeToggle />
              </div>
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
          Sohbet dışı sayfalarda iç sarmalayıcı header'ın px-4/sm:px-8 oluklarıyla
          hizalı, max-w-7xl sınırlı. */}
      <main
        className={cn(
          'relative z-10 flex min-h-0 flex-1 flex-col',
          isChat ? 'overflow-hidden' : 'overflow-y-auto',
        )}
      >
        <div
          className={cn(
            // Chat = "uygulama" görünümü: tam genişlik VE oluksuz — geçmiş rayı
            // sol kenara/header'ın hemen altına tam dayansın (Gemini deseni).
            // Oluğu burada değil, ChatPage'in kendi sütunlarında veriyoruz.
            // Diğer sayfalar okunur genişlikte ortalı + header ile hizalı kalır.
            //
            // min-h-0 YALNIZCA chat'te: chat kabının main'in yüksekliğine oturması (ve kendi
            // içinde kayması) için gerekli. Sohbet dışı sayfalarda ise bu sarmalayıcıyı main'in
            // yüksekliğine KIRPIYORDU — uzun içerik (rezervasyon formu) alt kenarından taşıyor,
            // py-8'in alt oluğu kırpılan sınırda kalıp kaydırılabilir alanın dışında kalıyordu:
            // sona kaydırıldığında son öğe (Önizlemeye geç butonu) ekranın dibine yapışıyordu.
            // min-height:auto ile sarmalayıcı içeriği kadar uzar, alt oluk da onunla birlikte kayar.
            'mx-auto flex w-full flex-1 flex-col',
            isChat ? 'min-h-0 max-w-none' : 'max-w-7xl px-4 py-8 sm:px-8',
          )}
        >
          {/* Rota geçişi: mode="wait" + pathname key — AnimatedOutlet ayrılan
              kopyada eski sayfayı dondurur (frozen outlet deseni). flex-1: kısa
              sayfalar yüksekliği doldurur. min-h-0 yalnız chat'te — sarmalayıcıyla
              aynı gerekçe (bkz. üstteki not). */}
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={location.pathname}
              className={cn('flex flex-1 flex-col', isChat && 'min-h-0')}
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
