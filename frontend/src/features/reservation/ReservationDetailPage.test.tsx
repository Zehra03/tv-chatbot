import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
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
import { ReservationDetailPage } from '@/features/reservation/ReservationDetailPage'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

function renderPage(id: string, state?: { justBooked?: boolean }) {
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
        <MemoryRouter initialEntries={[{ pathname: `/reservations/${id}`, state }]}>
          <Routes>
            <Route path="/reservations/:id" element={<ReservationDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
}

describe('ReservationDetailPage (MSW ile)', () => {
  it('rezervasyonun tam dökümünü gösterir (otel bloğu + misafirler)', async () => {
    renderPage('1003')

    expect(await screen.findByText('PAX-MOCK-1003', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('İptal edildi')).toBeTruthy()
    // "Otel" iki yerde: ürün-tipi alanı + otel bloğu başlığı.
    expect(screen.getAllByText('Otel').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/800/)).toBeTruthy()

    // Otel snapshot bloğu.
    expect(screen.getByText('Test Seaside Hotel Bodrum · 4★')).toBeTruthy()

    // Misafir dökümü: yetişkin + çocuk, iletişim yalnızca ana misafirde.
    expect(screen.getByText('Sample Guest')).toBeTruthy()
    expect(screen.getByText(/Yetişkin · 45 yaş · DE/)).toBeTruthy()
    expect(screen.getByText('Mini Guest')).toBeTruthy()
    expect(screen.getByText(/Çocuk · 8 yaş/)).toBeTruthy()
    expect(screen.getByText(/sample\.guest@example\.com/)).toBeTruthy()

    // İptal edilmiş → iptal bölümü çıkmaz.
    expect(screen.queryByRole('button', { name: 'Rezervasyonu iptal et' })).toBeNull()
  })

  it('onaylı rezervasyon iptal edilebilir (sebep + açık onay → PATCH → durum İptal edildi)', async () => {
    const user = userEvent.setup()
    renderPage('1001')

    expect(await screen.findByText('PAX-MOCK-1001', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('Onaylandı')).toBeTruthy()

    // İptal bölümü: sebep seçili (varsayılan), açık onay kutusu iptal butonunu açar.
    const cancelBtn = screen.getByRole('button', { name: 'Rezervasyonu iptal et' })
    expect((cancelBtn as HTMLButtonElement).disabled).toBe(true)
    await user.click(screen.getByRole('checkbox'))
    expect((cancelBtn as HTMLButtonElement).disabled).toBe(false)

    await user.click(cancelBtn)

    // İptal sonrası detay tazelenir → durum "İptal edildi", iptal bölümü kaybolur.
    await waitFor(
      () => expect(screen.getByText('İptal edildi')).toBeTruthy(),
      { timeout: 3000 },
    )
    expect(screen.queryByRole('button', { name: 'Rezervasyonu iptal et' })).toBeNull()
  })

  it('kesin onaydan sonra (justBooked) tek seferlik "alındı" bandı gösterir', async () => {
    renderPage('1001', { justBooked: true })

    // Detay yüklenince LoadingState (o da role="status") kalkar; geriye yalnız band kalır.
    expect(await screen.findByText('PAX-MOCK-1001', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByRole('status').textContent).toContain('Rezervasyonunuz alındı')
  })

  it('doğrudan açılışta (justBooked yok) "alındı" bandı gösterilmez', async () => {
    renderPage('1001')

    expect(await screen.findByText('PAX-MOCK-1001', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('bulunamayan rezervasyonda hata gösterir', async () => {
    renderPage('9999')

    const alert = await screen.findByRole('alert', {}, { timeout: 3000 })
    expect(alert.textContent).toContain('Rezervasyon bulunamadı.')
    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeTruthy()
  })
})
