import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { ReservationDetailPage } from '@/features/reservation/ReservationDetailPage'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

function renderPage(id: string) {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      reservationDraft: reservationDraftReducer,
      ui: uiReducer,
    },
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/reservations/${id}`]}>
          <Routes>
            <Route path="/reservations/:id" element={<ReservationDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
}

describe('ReservationDetailPage (MSW ile)', () => {
  it('rezervasyonun tam dökümünü gösterir (misafirler dahil)', async () => {
    renderPage('1003')

    expect(await screen.findByText('PAX-MOCK-1003', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('İptal edildi')).toBeTruthy()
    expect(screen.getByText('Otel')).toBeTruthy()
    expect(screen.getByText(/800/)).toBeTruthy()

    // Misafir dökümü: yetişkin + çocuk, iletişim yalnızca ana misafirde.
    expect(screen.getByText('Sample Guest')).toBeTruthy()
    expect(screen.getByText(/Yetişkin · 45 yaş · DE/)).toBeTruthy()
    expect(screen.getByText('Mini Guest')).toBeTruthy()
    expect(screen.getByText(/Çocuk · 8 yaş/)).toBeTruthy()
    expect(screen.getByText(/sample\.guest@example\.com/)).toBeTruthy()
  })

  it('bulunamayan rezervasyonda hata gösterir', async () => {
    renderPage('9999')

    const alert = await screen.findByRole('alert', {}, { timeout: 3000 })
    expect(alert.textContent).toContain('Rezervasyon bulunamadı.')
    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeTruthy()
  })
})
