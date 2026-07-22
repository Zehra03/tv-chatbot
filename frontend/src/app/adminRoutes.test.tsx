import { describe, expect, it } from 'vitest'
import { isValidElement, type ReactElement } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryRouter, type RouteObject } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { routes } from '@/app/router'
import { Layout } from '@/components/Layout'
import { AdminLayout } from '@/features/admin/AdminLayout'
import authReducer, { type SessionUser } from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'

function elementType(el: React.ReactNode) {
  return isValidElement(el) ? (el as ReactElement).type : undefined
}

/**
 * Admin dalının YERLEŞİMİ. Paneli ana Layout'un altına taşımak (doğal görünen bir sadeleştirme)
 * iki navigasyonu — üst bar ve sidebar — iç içe render ederdi; bu testler onu yakalar.
 */
describe('admin rota yerleşimi', () => {
  const root = routes[0]
  const protectedWrapper = root.children!.find(
    (r) => !r.path && !r.index && r.children,
  ) as RouteObject

  /** ProtectedRoute'un altındaki, AdminLayout taşıyan kardeş dal. */
  function adminBranch() {
    return protectedWrapper.children!.find((r) =>
      r.children?.some((c) => elementType(c.element) === AdminLayout),
    ) as RouteObject
  }

  it('admin dalı ana Layout ile KARDEŞ, altında değil', () => {
    const layoutRoute = protectedWrapper.children!.find(
      (r) => elementType(r.element) === Layout,
    ) as RouteObject
    const adminPaths = JSON.stringify(layoutRoute)
    expect(adminPaths).not.toContain('/admin')
    expect(adminBranch()).toBeTruthy()
  })

  it('dört modül rotası da AdminLayout altında tanımlı', () => {
    const adminLayoutRoute = adminBranch().children!.find(
      (r) => elementType(r.element) === AdminLayout,
    ) as RouteObject
    const boundary = adminLayoutRoute.children!.find((r) => r.errorElement) as RouteObject
    const paths = boundary.children!.map((r) => r.path)
    expect(paths).toEqual(
      expect.arrayContaining(['/admin', '/admin/flights', '/admin/reservations', '/admin/users']),
    )
  })
})

/**
 * Yetki DAVRANIŞI (kabul kriteri): panele yalnızca ROLE_ADMIN girer, yetkisiz /login'e düşer.
 *
 * Bu bir görünürlük kapısıdır; gerçek koruma backend'de (`/api/v1/admin/**` → hasRole('ADMIN')).
 * Yine de test edilmeli: bozulursa yetkisiz kullanıcı her isteği 403 dönen bir panelde dolaşır.
 */
describe('RequireAdmin', () => {
  function renderAt(path: string, user: SessionUser | null) {
    const store = configureStore({
      reducer: {
        auth: authReducer,
        chat: chatReducer,
        reservationDraft: reservationDraftReducer,
        ui: uiReducer,
      },
      preloadedState: user
        ? { auth: { user, token: 'test-token', refreshToken: 'test-refresh', guestId: null } }
        : undefined,
    })
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const router = createMemoryRouter(routes, { initialEntries: [path] })
    render(
      <Provider store={store}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </Provider>,
    )
    return router
  }

  /**
   * Oturumsuz ziyaretçi /login'de KALIR — kabul kriterinin asıl hâli. (Bu dalda kapıyı zaten
   * ProtectedRoute tutar; RequireAdmin'in arkasındaki ikinci kilit onun için de geçerli.)
   */
  it('oturumsuz ziyaretçi /admin yerine /login görür', async () => {
    const router = renderAt('/admin', null)
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  /**
   * Giriş yapmış ama yetkisiz kullanıcı: RequireAdmin onu /login'e yollar, LoginPage ise gerçek
   * oturumu olanı uygulamaya geri gönderir (LoginPage: `if (user && !user.guest) → /chat`), yani
   * son durak /chat olur. Önemli olan değişmez: panele ASLA girmez.
   */
  it('ROLE_USER hesabı panele giremez', async () => {
    const router = renderAt('/admin', {
      id: '2',
      email: 'user@example.com',
      name: 'Normal',
      role: 'USER',
    })
    await waitFor(() => expect(router.state.location.pathname).not.toBe('/admin'))
    expect(screen.queryByText('Uçuş Yönetimi')).toBeNull()
  })

  it('misafir oturumu panele giremez', async () => {
    const router = renderAt('/admin', {
      id: 'guest',
      email: '',
      name: 'Misafir',
      guest: true,
    })
    await waitFor(() => expect(router.state.location.pathname).not.toBe('/admin'))
  })

  it('ROLE_ADMIN hesabı panele girer', async () => {
    const router = renderAt('/admin', {
      id: '1',
      email: 'admin@paxassist.test',
      name: 'Yönetici',
      role: 'ADMIN',
    })
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin'))
    // Kabuk gerçekten render oldu mu — sidebar'daki modül bağlantıları yerinde.
    expect(await screen.findByRole('link', { name: 'Kullanıcılar' })).toBeTruthy()
  })
})
