import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import type { ZoneHandle } from '@/app/zones'
import { Layout } from '@/components/Layout'
import LoginPage from '@/features/auth/LoginPage'
import Design from '@/pages/Design'
import { ChatPage } from '@/features/chat/ChatPage'
import { HotelsPage } from '@/features/hotels/HotelsPage'
import { FlightsPage } from '@/features/flights/FlightsPage'
import { ReservationFormPage } from '@/features/reservation/ReservationFormPage'
import { ReservationsPage } from '@/features/reservation/ReservationsPage'
import { ReservationDetailPage } from '@/features/reservation/ReservationDetailPage'

/**
 * Korumalı rota sarmalı — state'te mock kullanıcı yoksa /login'e yönlendirir
 * (docs/frontend-architecture.md §3). Provider içinde render edildiği için
 * useAppSelector güvenle çalışır.
 */
function ProtectedRoute() {
  const user = useAppSelector((s) => s.auth.user)
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

export const router = createBrowserRouter([
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
          { index: true, element: <Navigate to="/chat" replace /> },
          // Bölge işaretleri (src/app/zones.ts): 'ai' = koyu gece uçuşu yüzeyi
          // (chat + arama sonuçları), 'controlled' = açık rezervasyon yüzeyi.
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
            path: '/reservation/new',
            element: <ReservationFormPage />,
            handle: { zone: 'controlled' } satisfies ZoneHandle,
          },
          {
            path: '/reservations',
            element: <ReservationsPage />,
            handle: { zone: 'controlled' } satisfies ZoneHandle,
          },
          {
            path: '/reservations/:id',
            element: <ReservationDetailPage />,
            handle: { zone: 'controlled' } satisfies ZoneHandle,
          },
        ],
      },
    ],
  },
  // Bilinmeyen yollar → /chat (oturum yoksa oradan /login'e düşer).
  { path: '*', element: <Navigate to="/chat" replace /> },
])
