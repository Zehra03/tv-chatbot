import {
  createBrowserRouter,
  Navigate,
  Outlet,
  useLocation,
  type RouteObject,
} from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import type { ZoneHandle } from '@/app/zones'
import { Layout } from '@/components/Layout'
import LoginPage from '@/features/auth/LoginPage'
import LandingPage from '@/pages/LandingPage'
import Design from '@/pages/Design'
import { RouteErrorPage } from '@/pages/RouteErrorPage'
import { ChatPage } from '@/features/chat/ChatPage'
import { HotelsPage } from '@/features/hotels/HotelsPage'
import { FlightsPage } from '@/features/flights/FlightsPage'
import { ReservationFormPage } from '@/features/reservation/ReservationFormPage'
import { ReservationsPage } from '@/features/reservation/ReservationsPage'
import { ReservationDetailPage } from '@/features/reservation/ReservationDetailPage'
import { ReservationPrintPage } from '@/features/reservation/ReservationPrintPage'
import { ProfilePage } from '@/features/profile/ProfilePage'

/**
 * Korumalı rota sarmalı — state'te mock kullanıcı yoksa /login'e yönlendirir
 * (docs/frontend-architecture.md §3). Provider içinde render edildiği için
 * useAppSelector güvenle çalışır.
 */
function ProtectedRoute() {
  const user = useAppSelector((s) => s.auth.user)
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

/**
 * Gerçek hesap gerektiren rota sarmalı — sohbet/arama misafire açıktır ama rezervasyon
 * ve profil hesap ister ("kontrollü rezervasyon" ilkesi; rezervasyonu sonradan yalnızca
 * kayıtlı kullanıcı görüntüleyebilir). Misafiri /login'e yönlendirir ve giriş/kayıt sonrası
 * geldiği sayfaya dönebilmesi için nereden geldiğini state'te taşır.
 */
function RequireAccount() {
  const user = useAppSelector((s) => s.auth.user)
  const location = useLocation()
  if (user && !user.guest) return <Outlet />
  return (
    <Navigate
      to="/login"
      replace
      state={{ reason: 'account-required', from: location.pathname }}
    />
  )
}

/** Rota ağacı ayrıca export edilir ki hata sınırı yerleşimi test edilebilsin. */
export const routes: RouteObject[] = [
  {
    // Kök hata sınırı — Login/Layout dahil her yerde varsayılan İngilizce
    // hata ekranı yerine RouteErrorPage görünür (chrome'suz, tam sayfa).
    errorElement: <RouteErrorPage />,
    children: [
      // Herkese açık tanıtım sayfası; tüm CTA'ları /chat'e akar (oturum yoksa
      // ProtectedRoute /login'e düşürür).
      { path: '/', element: <LandingPage /> },
      { path: '/login', element: <LoginPage /> },
      // Tasarım playground'u — korumasız, screenshot/iterasyon hedefi.
      { path: '/design', element: <Design /> },
      {
        element: <ProtectedRoute />,
        children: [
          {
            // Ortak çatı (header + navigasyon); korumalı sayfalar bunun içinde render olur.
            element: <Layout />,
            children: [
              {
                // Sayfa hataları buraya yükselir → kart Layout'un Outlet'inde
                // render olur, header/nav korunur.
                errorElement: <RouteErrorPage />,
                children: [
                  // '/' artık herkese açık LandingPage'in — korumalı index
                  // yönlendirmesi kaldırıldı ki iki rota aynı yolu yarıştırmasın.
                  // Bölge işaretleri (src/app/zones.ts): sahibin kararıyla TÜM rotalar
                  // koyu gece uçuşu yüzeyinde ('ai'). "AI devre dışı" anlatısı artık
                  // yüzey rengiyle değil AiOffBanner ile verilir; 'controlled' mekanizması
                  // ileride gerekirse duruyor.
                  {
                    path: '/chat',
                    element: <ChatPage />,
                    handle: { zone: 'ai' } satisfies ZoneHandle,
                  },
                  {
                    path: '/hotels',
                    element: <HotelsPage />,
                    handle: { zone: 'ai' } satisfies ZoneHandle,
                  },
                  {
                    path: '/flights',
                    element: <FlightsPage />,
                    handle: { zone: 'ai' } satisfies ZoneHandle,
                  },
                  {
                    // Hesap gerektiren sayfalar: misafir buraya giremez, /login'e düşer.
                    element: <RequireAccount />,
                    children: [
                      {
                        path: '/reservation/new',
                        element: <ReservationFormPage />,
                        handle: { zone: 'ai' } satisfies ZoneHandle,
                      },
                      {
                        path: '/reservations',
                        element: <ReservationsPage />,
                        handle: { zone: 'ai' } satisfies ZoneHandle,
                      },
                      {
                        path: '/reservations/:id',
                        element: <ReservationDetailPage />,
                        handle: { zone: 'ai' } satisfies ZoneHandle,
                      },
                      {
                        path: '/profile',
                        element: <ProfilePage />,
                        handle: { zone: 'ai' } satisfies ZoneHandle,
                      },
                    ],
                  },
                ],
              },
            ],
          },
          {
            // Yazdırma voucher'ı — bilinçli olarak Layout'un DIŞINDA, kardeş dalda.
            // Kabuk (header + h-screen overflow-hidden + koyu gece-uçuşu yüzeyi)
            // kâğıda taşınamaz; chrome'suz açılınca sayfa :root'un açık token'larını
            // alır ve voucher beyaz zeminde koyu yazı olur. Hesap koruması detay
            // sayfasıyla aynı: rezervasyonu yalnız kayıtlı kullanıcı basabilir.
            // Sıralama derdi yok — react-router özgüllüğe göre eşler ve
            // '/reservations/:id' iki segmentli yolu zaten tam eşleşmeyle sınırlıdır.
            element: <RequireAccount />,
            children: [
              { path: '/reservations/:id/print', element: <ReservationPrintPage /> },
            ],
          },
        ],
      },
      // Bilinmeyen yollar → /chat (oturum yoksa oradan /login'e düşer).
      { path: '*', element: <Navigate to="/chat" replace /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
