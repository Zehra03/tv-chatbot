import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { logout } from '@/features/auth/authSlice'
import { cn } from '@/lib/utils'

/** Korumalı sayfaların ortak çatısı: üst bar (logo + navigasyon + kullanıcı/çıkış)
 * ve içerik alanı. İçerik <Outlet /> ile buraya render edilir. */
const NAV = [
  { to: '/chat', label: 'Sohbet' },
  { to: '/hotels', label: 'Oteller' },
  { to: '/flights', label: 'Uçuşlar' },
  { to: '/reservations', label: 'Rezervasyonlar' },
]

export function Layout() {
  const user = useAppSelector((s) => s.auth.user)
  const dispatch = useAppDispatch()
  const navigate = useNavigate()

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="container flex h-16 items-center justify-between gap-4">
          <div className="flex items-center gap-8">
            <NavLink to="/chat" aria-label="Ana sayfa">
              <Logo height={32} />
            </NavLink>
            <nav className="flex items-center gap-1">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    cn(
                      'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-accent text-accent-foreground'
                        : 'text-muted-foreground hover:text-foreground',
                    )
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            {user && (
              <span className="hidden text-sm text-muted-foreground sm:inline">{user.email}</span>
            )}
            <Button variant="outline" size="sm" onClick={handleLogout}>
              Çıkış
            </Button>
          </div>
        </div>
      </header>
      <main className="container py-8">
        <Outlet />
      </main>
    </div>
  )
}
