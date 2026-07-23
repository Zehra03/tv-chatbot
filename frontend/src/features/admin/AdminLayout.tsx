import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { CalendarCheck, LayoutDashboard, Menu, UserRound, Users, X } from 'lucide-react'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { Logo } from '@/components/Logo'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { useAppSelector } from '@/app/hooks'
import { useLogout } from '@/features/auth/useLogout'
import { cn } from '@/lib/utils'

/**
 * Admin panelinin kabuğu: solda modül menüsü (sidebar), üstte sayfa başlığı + profil/çıkış.
 *
 * Ana `components/Layout.tsx`'ten AYRI bir kabuk — o üst barlı ve gezinme ortada, panel ise
 * yan menülü. Rota ağacında da kardeş dalda dururlar (app/router.tsx), böylece iki navigasyon
 * hiçbir zaman iç içe render edilmez.
 *
 * Kaydırma sözleşmesi Layout ile AYNI ve bilerek öyle: gövde asla kaymaz (`h-screen` +
 * `overflow-hidden`), yalnızca `<main>` kayar. Admin tabloları uzundur; iki ayrı kaydırma kabı
 * olsaydı sidebar içerikle birlikte yukarı kaçardı.
 */
const NAV = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/reservations', label: 'Rezervasyonlar', icon: CalendarCheck, end: false },
  { to: '/admin/users', label: 'Kullanıcılar', icon: Users, end: false },
]

/** Başlık çubuğundaki sayfa adı — rotadan türer, her sayfa ayrıca kendi h1'ini taşır. */
const TITLES: Record<string, string> = {
  '/admin': 'Dashboard',
  '/admin/reservations': 'Rezervasyon Yönetimi',
  '/admin/users': 'Kullanıcı Yönetimi',
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-primary/10 text-primary'
      : 'text-muted-foreground hover:bg-muted hover:text-foreground',
  )

export function AdminLayout() {
  const user = useAppSelector((s) => s.auth.user)
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const handleLogout = useLogout()

  const title = TITLES[location.pathname] ?? 'Yönetim'

  const navItems = (
    <>
      {NAV.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={navLinkClass}
          onClick={() => setMenuOpen(false)}
        >
          <item.icon className="h-4 w-4 shrink-0" aria-hidden />
          <span>{item.label}</span>
        </NavLink>
      ))}
    </>
  )

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-background text-foreground supports-[height:100dvh]:h-[100dvh]">
      <header className="relative z-40 shrink-0 border-b border-border bg-background">
        <div className="flex h-16 w-full items-center justify-between gap-4 px-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            {/* Mobil menü düğmesi — md+ ekranda sidebar zaten kalıcı. */}
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
            {/* Logo → uygulamanın kendisine dönüş; panel ayrı bir dünya değil. */}
            <NavLink to="/chat" aria-label="Uygulamaya dön" className="hidden shrink-0 sm:block">
              <Logo height={28} />
            </NavLink>
            <h2 className="truncate text-base font-semibold text-foreground">{title}</h2>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <ThemeToggle />
            {user && (
              <NavLink
                to="/profile"
                className="hidden max-w-[14rem] items-center gap-1.5 truncate rounded-md px-2 py-1 text-sm text-muted-foreground transition-colors hover:text-foreground sm:flex"
              >
                <UserRound className="h-4 w-4 shrink-0" aria-hidden />
                <span className="truncate">{user.name ?? user.email}</span>
              </NavLink>
            )}
            <Button variant="outline" size="sm" onClick={handleLogout}>
              Çıkış
            </Button>
          </div>
        </div>

        {/* Mobil menü — overlay; header yüksekliği sabit kalsın diye absolute. */}
        {menuOpen && (
          <div className="absolute inset-x-0 top-full z-50 border-b border-border bg-background shadow-soft md:hidden">
            <nav className="flex flex-col gap-1 px-4 py-3">{navItems}</nav>
          </div>
        )}
      </header>

      <div className="flex min-h-0 flex-1">
        {/* Kalıcı sidebar — yalnızca md+; kendi içinde kayar ki uzun menü içeriği ezmesin. */}
        <aside className="hidden w-60 shrink-0 overflow-y-auto border-r border-border bg-card md:block">
          <nav className="flex flex-col gap-1 p-3">{navItems}</nav>
        </aside>

        {/* Tek kaydırma kabı. min-w-0: geniş tablolar sidebar'ı ittirmesin, kendi içinde kaysın. */}
        <main className="min-w-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6">
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
          </div>
        </main>
      </div>
    </div>
  )
}
