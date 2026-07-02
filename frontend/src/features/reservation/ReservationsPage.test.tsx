import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { ReservationsPage } from '@/features/reservation/ReservationsPage'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

function renderPage() {
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
        <MemoryRouter initialEntries={['/reservations']}>
          <Routes>
            <Route path="/reservations" element={<ReservationsPage />} />
            <Route path="/reservations/:id" element={<div>DETAY STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
}

describe('ReservationsPage (MSW ile)', () => {
  it('fixture rezervasyonlarını sütunlarıyla listeler', async () => {
    renderPage()

    expect(await screen.findByText('PAX-MOCK-1001', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('PAX-MOCK-1002')).toBeTruthy()
    expect(screen.getByText('PAX-MOCK-1003')).toBeTruthy()

    // Sütun başlıkları.
    for (const col of ['No', 'Tip', 'Tarih', 'Misafir', 'Toplam', 'Durum']) {
      expect(screen.getByRole('columnheader', { name: col })).toBeTruthy()
    }

    // Satır içerikleri: tip, misafir, durum etiketi.
    expect(screen.getAllByText('Otel').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('Ayşe Mock')).toBeTruthy()
    expect(screen.getByText('Onaylandı')).toBeTruthy()
    expect(screen.getByText('Beklemede')).toBeTruthy()
    expect(screen.getByText('İptal edildi')).toBeTruthy()
  })

  it('Detay bağlantısı ilgili rezervasyonun detayına gider', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('PAX-MOCK-1001', {}, { timeout: 3000 })
    // Detay gerçek bir bağlantıdır (yeni sekmede açılabilir, link semantiği).
    await user.click(screen.getAllByRole('link', { name: 'Detay' })[0])
    expect(await screen.findByText('DETAY STUB')).toBeTruthy()
  })
})
