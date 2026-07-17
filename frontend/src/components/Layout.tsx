import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Menu, UserRound, X } from 'lucide-react'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { GooeyNav } from '@/components/GooeyNav'
import { Logo } from '@/components/Logo'
import { SkyBackground } from '@/components/NightSkyBackground'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { useAppSelector } from '@/app/hooks'
import { useTheme } from '@/app/theme'
import { useLogout } from '@/features/auth/useLogout'
import { cn } from '@/lib/utils'

/** Korumalı sayfaların ortak çatısı: üst bar (logo + navigasyon + kullanıcı/çıkış)
 * ve içerik alanı. İçerik <AnimatedOutlet /> ile buraya render edilir.
 * Responsive: md+ satır içi navigasyon; mobilde hamburger ile açılan panel.
 *
 * Yüzey rengi KULLANICI TEMASINDAN gelir (app/theme.tsx), rotadan değil. Önceden
 * rotanın `handle.zone` işareti boyardı ("AI bölgesi koyu, kontrollü bölge açık");
 * tüm rotalar 'ai' işaretlenip anlatı AiOffBanner'a taşınınca o mekanizma tek
 * renge çöktü ve kaldırıldı.
 *
 * Kabuk artık tema dalı taşımıyor — renkler token'dan geliyor ve ikisini de doğru
 * veriyor. Logo da istisna değil: eskiden lacivert harfleri koyu yüzeyde okunur
 * kılmak için arkasına beyaz halo konurdu; artık Logo'nun kendi koyu varyantı
 * (beyaz harfli kaynak) var, halo kaldırıldı. */
const NAV = [
  { to: '/chat', label: 'Sohbet' },
  { to: '/hotels', label: 'Oteller' },
  { to: '/flights', label: 'Uçuşlar' },
  { to: '/reservations', label: 'Rezervasyonlar' },
]

/** Mobil panel nav: her iki temada da pill (dikey listede alt çizgi okunmaz).
 *  Renkler token'dan geldiği için tema dalı gerekmiyor. */
const mobileNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-primary/10 text-primary'
      : 'text-muted-foreground hover:text-foreground',
  )

export function Layout() {
  const user = useAppSelector((s) => s.auth.user)
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  const { resolvedTheme } = useTheme()

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

  const handleLogout = useLogout()

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
        // Kabuk artık elle boyanmıyor: koyu --background = brand-navy, --foreground
        // = beyaz (index.css'teki Gece Uçuşu paleti), yani token'lar iki temayı da
        // doğru veriyor.
        'bg-background text-foreground',
      )}
    >
      {/* Gökyüzü — her iki temada da var (gece/gündüz), tema değişiminde çapraz
          geçişle. Açık temada bu zemin ŞART: cam yüzeyler beyaz/0.65, saf beyaz
          üstünde görünmezlerdi. */}
      <AnimatePresence mode="wait">
        <motion.div
          key={resolvedTheme}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.7 }}
        >
          <SkyBackground />
        </motion.div>
      </AnimatePresence>

      <header
        className={cn(
          // Kabuğun sabit üst satırı — flex sütununda küçülmez (shrink-0); sticky
          // yerine gerçek akış elemanı, çünkü main artık tek kaydırma kabı.
          // Düz (flat): dolu zemin + alt kenarlık, cam/blur yok. Renkler token'dan.
          'relative z-40 shrink-0 border-b border-border bg-background transition-colors duration-700',
        )}
      >
        {/* Bar tam genişlik (container sınırı yok) — h-24; ChatPage 10rem hesabı buna bağlı. */}
        <div className="relative flex h-24 w-full items-center justify-between gap-4 px-4 sm:px-8">
          <NavLink to="/" aria-label="Ana sayfa" onClick={() => setMenuOpen(false)}>
            {/* Halo YOK: Logo 'auto' varyantla koyu zeminde beyaz harfli kaynağa
                geçiyor, okunurluk artık görselin kendisinden geliyor. */}
            <Logo height={34} />
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
                    isActive ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
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
              className="text-muted-foreground hover:bg-muted hover:text-foreground md:hidden"
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
          <div className="absolute inset-x-0 top-full z-50 border-b border-t border-border bg-background shadow-soft md:hidden">
            <nav className="flex flex-col gap-1 px-4 py-3 sm:px-8">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={mobileNavLinkClass}
                  onClick={() => setMenuOpen(false)}
                >
                  {item.label}
                </NavLink>
              ))}
              {/* Tema seçici — mobil panelde kendi satırında. */}
              <div className="mt-2 flex items-center justify-between gap-2 border-t border-border px-3 pt-3">
                <span className="text-sm text-muted-foreground">Tema</span>
                <ThemeToggle />
              </div>
              <div className="mt-2 flex items-center justify-between gap-2 border-t border-border pt-3">
                {/* Kullanıcı adı → profil sayfası (panel kapanır). */}
                {user && (
                  <NavLink
                    to="/profile"
                    onClick={() => setMenuOpen(false)}
                    className="flex min-w-0 items-center gap-1.5 truncate px-3 text-sm text-muted-foreground transition-colors hover:text-foreground"
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
