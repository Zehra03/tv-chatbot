import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
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
          { path: '/chat', element: <ChatPage /> },
          { path: '/hotels', element: <HotelsPage /> },
          { path: '/flights', element: <FlightsPage /> },
          { path: '/reservation/new', element: <ReservationFormPage /> },
          { path: '/reservations', element: <ReservationsPage /> },
          { path: '/reservations/:id', element: <ReservationDetailPage /> },
        ],
      },
    ],
  },
  // Bilinmeyen yollar → /chat (oturum yoksa oradan /login'e düşer).
  { path: '*', element: <Navigate to="/chat" replace /> },
])
