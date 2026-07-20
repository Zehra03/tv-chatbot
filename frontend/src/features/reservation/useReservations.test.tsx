import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer, { logout, sessionStarted } from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import type { ReservationSummary } from '@/types'
import { ReservationsPage } from '@/features/reservation/ReservationsPage'

/**
 * Kimlik sınırı regresyonu: aynı tarayıcıda A çıkıp B girdiğinde, A'nın cache'lenmiş
 * rezervasyonları B'ye SIZMAMALI. Query key kimlik taşımadığında (eski hâl: ['reservations'])
 * React Query B'ye A'nın girdisini anında döndürüyordu — ReservationsPage spinner'ı
 * `isFetching && !data` ile geçtiği için yükleme bile görünmüyor, B doğrudan A'nın
 * rezervasyon numarasını/tutarını/misafir adını okuyordu (gcTime 5 dk).
 */

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const USER_A = { id: 'user-a', email: 'a@example.com', name: 'Ayşe' }
const USER_B = { id: 'user-b', email: 'b@example.com', name: 'Burak' }

const summary = (over: Partial<ReservationSummary>): ReservationSummary => ({
  id: 1,
  reservationNumber: 'PAX-A-0001',
  status: 'confirmed',
  productType: 'hotel',
  reservationDate: '2026-06-20',
  totalAmount: 1200,
  currency: 'EUR',
  leadGuestName: 'Ayşe Mock',
  ...over,
})

const RES_A = summary({ id: 1, reservationNumber: 'PAX-A-0001', leadGuestName: 'Ayşe Mock' })
const RES_B = summary({ id: 2, reservationNumber: 'PAX-B-0002', leadGuestName: 'Burak Mock' })

/** Jetona göre farklı kullanıcı verisi döndürür — gerçek backend'in yaptığı gibi. */
function serveByToken() {
  server.use(
    http.get('*/api/v1/reservations', ({ request }) => {
      const auth = request.headers.get('Authorization')
      return HttpResponse.json(auth === 'Bearer token-b' ? [RES_B] : [RES_A])
    }),
  )
}

function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      reservationDraft: reservationDraftReducer,
      ui: uiReducer,
    },
  })
}

function renderPage(store: ReturnType<typeof makeStore>) {
  // TEK queryClient — aynı tarayıcı sekmesini temsil eder; cache oturumlar arası yaşar.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/reservations']}>
          <Routes>
            <Route path="/reservations" element={<ReservationsPage />} />
            <Route path="/reservations/:id" element={<div>DETAY STUB</div>} />
            <Route path="/reservations/:id/print" element={<div>VOUCHER STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return queryClient
}

describe('useReservations — kimlik sınırı', () => {
  it('A çıkıp B girince A’nın rezervasyonlarını B’ye göstermez', async () => {
    serveByToken()
    const store = makeStore()
    act(() => {
      store.dispatch(sessionStarted({ user: USER_A, token: 'token-a', refreshToken: 'refresh-a' }))
    })
    renderPage(store)

    // A kendi listesini görüyor.
    expect(await screen.findByText('PAX-A-0001')).toBeTruthy()

    // A çıkıyor, B aynı sekmede giriyor.
    act(() => {
      store.dispatch(logout())
      store.dispatch(sessionStarted({ user: USER_B, token: 'token-b', refreshToken: 'refresh-b' }))
    })

    // Kimlik değişti → key değişti → A'nın girdisi B'ye ASLA render edilmez.
    // (Regresyonda bu satır patlar: A'nın verisi cache'ten anında geri gelirdi.)
    expect(screen.queryByText('PAX-A-0001')).toBeNull()
    expect(screen.queryByText('Ayşe Mock')).toBeNull()

    // B kendi listesini alır.
    expect(await screen.findByText('PAX-B-0002')).toBeTruthy()
    await waitFor(() => expect(screen.queryByText('PAX-A-0001')).toBeNull())
  })

  it('query key aktif kimliği taşır — ayrı kimlik, ayrı cache girdisi', async () => {
    serveByToken()
    const store = makeStore()
    act(() => {
      store.dispatch(sessionStarted({ user: USER_A, token: 'token-a', refreshToken: 'refresh-a' }))
    })
    const queryClient = renderPage(store)

    expect(await screen.findByText('PAX-A-0001')).toBeTruthy()

    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map((q) => q.queryKey)
      .filter((k) => Array.isArray(k) && k[0] === 'reservations')

    expect(keys).toContainEqual(['reservations', 'user-a'])
    // Kimliksiz ham anahtar bir daha kullanılmamalı — çakışmanın kaynağı oydu.
    expect(keys).not.toContainEqual(['reservations'])
  })
})
