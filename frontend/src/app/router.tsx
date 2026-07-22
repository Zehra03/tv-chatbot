import {
  createBrowserRouter,
  Navigate,
  Outlet,
  useLocation,
  type RouteObject,
} from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
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
 * Korumalı rota sarmalı — hiç oturum yoksa /login'e yönlendirir
 * (docs/frontend-architecture.md §3). Provider içinde render edildiği için
 * useAppSelector güvenle çalışır.
 *
 * DIŞ bekçi olduğundan, soğuk açılan bir derin bağlantıda (yer imi, paylaşılan link)
 * RequireAccount'tan ÖNCE çalışır — istenen adresi taşımazsa giriş sonrası herkes /chat'e
 * düşer. Bu yüzden RequireAccount ile aynı `from` sözleşmesini kullanır; LoginPage.goToApp
 * ikisini de aynı şekilde okur. `search` de taşınır: /hotels gibi sayfalar kriterlerini
 * query'de tutuyor, yalnız pathname dönmek kullanıcıyı boş bir aramaya bırakırdı.
 */
function ProtectedRoute() {
  const user = useAppSelector((s) => s.auth.user)
  const location = useLocation()
  if (user) return <Outlet />
  return (
    <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  )
}

/**
 * Gerçek hesap gerektiren rota sarmalı — sohbet/arama/rezervasyon KURMA misafire açıktır ama
 * rezervasyon GEÇMİŞİ ve profil hesap ister: bu sayfalar kimliğe bağlı kalıcı veriyi listeler
 * ve misafirin taşıyıcı anahtarı (X-Guest-Id) o veriyi sahiplenmeye yetmez. Misafiri /login'e
 * yönlendirir ve giriş/kayıt sonrası geldiği sayfaya dönebilmesi için nereden geldiğini
 * state'te taşır.
 *
 * `/reservation/new` bilerek DIŞARIDA: misafir de rezervasyon yapabilir, ama seçimi form
 * sayfasının kendi 0. adımında ("Giriş yap" / "Misafir devam et") açıkça yapar — kapıda sessizce
 * /login'e atılmaz. "Kontrollü rezervasyon" ilkesi korunur: booking'i hâlâ YALNIZ bu form yapar.
 */
function RequireAccount() {
  const user = useAppSelector((s) => s.auth.user)
  const location = useLocation()
  if (user && !user.guest) return <Outlet />
  return (
    <Navigate
      to="/login"
      replace
      state={{ reason: 'account-required', from: `${location.pathname}${location.search}` }}
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
                  {
                    path: '/chat',
                    element: <ChatPage />,
                  },
                  {
                    path: '/hotels',
                    element: <HotelsPage />,
                  },
                  {
                    path: '/flights',
                    element: <FlightsPage />,
                  },
                  {
                    // Misafire AÇIK: kapıda /login'e atmak yerine sayfa kendi 0. adımında
                    // "Giriş yap / Misafir devam et" seçimini sunar (GuestCheckoutChoice).
                    path: '/reservation/new',
                    element: <ReservationFormPage />,
                  },
                  {
                    // Hesap gerektiren sayfalar: misafir buraya giremez, /login'e düşer.
                    element: <RequireAccount />,
                    children: [
                      {
                        path: '/reservations',
                        element: <ReservationsPage />,
                      },
                      {
                        path: '/reservations/:id',
                        element: <ReservationDetailPage />,
                      },
                      {
                        path: '/profile',
                        element: <ProfilePage />,
                      },
                    ],
                  },
                ],
              },
            ],
          },
          {
            // Yazdırma voucher'ı — bilinçli olarak Layout'un DIŞINDA, kardeş dalda:
            // kabuk (header + h-screen overflow-hidden + gece-uçuşu yüzeyi) kâğıda
            // taşınamaz. Voucher'ın açık kalmasını artık Layout'un yokluğu DEĞİL,
            // sayfanın kendi `theme-light` sınıfı sağlar — `.dark` <html>'de yaşıyor
            // (app/theme.tsx) ve buraya da miras kalırdı. Hesap koruması detay
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
