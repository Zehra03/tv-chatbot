import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { logout } from '@/features/auth/authSlice'
import { cn } from '@/lib/utils'

/** Korumalı sayfaların ortak çatısı: üst bar (logo + navigasyon + kullanıcı/çıkış)
 * ve içerik alanı. İçerik <Outlet /> ile buraya render edilir.
 * Responsive: md+ satır içi navigasyon; mobilde hamburger ile açılan panel. */
const NAV = [
  { to: '/chat', label: 'Sohbet' },
  { to: '/hotels', label: 'Oteller' },
  { to: '/flights', label: 'Uçuşlar' },
  { to: '/reservations', label: 'Rezervasyonlar' },
]

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-accent text-accent-foreground'
      : 'text-muted-foreground hover:text-foreground',
  )

export function Layout() {
  const user = useAppSelector((s) => s.auth.user)
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="container flex h-16 items-center justify-between gap-4">
          <div className="flex items-center gap-8">
            <NavLink to="/chat" aria-label="Ana sayfa" onClick={() => setMenuOpen(false)}>
              <Logo height={32} />
            </NavLink>
            {/* Masaüstü navigasyonu — mobilde gizli, hamburger paneline taşınır. */}
            <nav className="hidden items-center gap-1 md:flex">
              {NAV.map((item) => (
                <NavLink key={item.to} to={item.to} className={navLinkClass}>
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            {user && (
              <span className="hidden text-sm text-muted-foreground md:inline">
                {user.name ?? user.email}
              </span>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              className="hidden md:inline-flex"
            >
              Çıkış
            </Button>
            {/* Mobil menü düğmesi */}
            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              aria-label={menuOpen ? 'Menüyü kapat' : 'Menüyü aç'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((o) => !o)}
            >
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </Button>
          </div>
        </div>

        {/* Mobil panel — linke tıklayınca kapanır. */}
        {menuOpen && (
          <div className="border-t md:hidden">
            <nav className="container flex flex-col gap-1 py-3">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={navLinkClass}
                  onClick={() => setMenuOpen(false)}
                >
                  {item.label}
                </NavLink>
              ))}
              <div className="mt-2 flex items-center justify-between border-t pt-3">
                {user && (
                  <span className="px-3 text-sm text-muted-foreground">
                    {user.name ?? user.email}
                  </span>
                )}
                <Button variant="outline" size="sm" onClick={handleLogout}>
                  Çıkış
                </Button>
              </div>
            </nav>
          </div>
        )}
      </header>
      <main className="container py-8">
        <Outlet />
      </main>
    </div>
  )
}
