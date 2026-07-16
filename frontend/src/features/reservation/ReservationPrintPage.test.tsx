import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { ReservationPrintPage } from '@/features/reservation/ReservationPrintPage'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

// jsdom'da window.print yok (varsa da "Not implemented" basar) — sayfanın kendini
// yazdırdığını doğrulayabilmek için sahte bir print kur.
let printSpy: ReturnType<typeof vi.fn>
beforeEach(() => {
  printSpy = vi.fn()
  vi.stubGlobal('print', printSpy)
})
afterEach(() => vi.unstubAllGlobals())

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
        <MemoryRouter initialEntries={[`/reservations/${id}/print`]}>
          <Routes>
            <Route path="/reservations/:id/print" element={<ReservationPrintPage />} />
            <Route path="/reservations/:id" element={<div>DETAY STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
}

describe('ReservationPrintPage (MSW ile)', () => {
  it('voucher’ı basar: PAX kodu, durum, otel ve misafirler', async () => {
    renderPage('1003')

    expect(await screen.findByText('PAX-MOCK-1003', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('Rezervasyon Özeti')).toBeTruthy()

    // Durum voucher'da rozetle taşınır (iptal edilmiş kayıt da basılabilmeli).
    expect(screen.getByText('İptal edildi')).toBeTruthy()

    // Otel snapshot'ı.
    expect(screen.getByText('Test Seaside Hotel Bodrum · 4★')).toBeTruthy()

    // Misafir tablosu: yetişkin + çocuk. Tabloya sınırla — ana misafirin adı
    // "Rezervasyon bilgileri" bloğunda (leadGuestName) da geçiyor.
    const guests = within(screen.getByRole('table'))
    expect(guests.getByText('Sample Guest')).toBeTruthy()
    expect(guests.getByText('Yetişkin')).toBeTruthy()
    expect(guests.getByText('Mini Guest')).toBeTruthy()
    expect(guests.getByText('Çocuk')).toBeTruthy()
  })

  it('veri gelince yazdırma diyaloğunu kendiliğinden açar', async () => {
    renderPage('1001')

    expect(await screen.findByText('PAX-MOCK-1001', {}, { timeout: 3000 })).toBeTruthy()
    await waitFor(() => expect(printSpy).toHaveBeenCalled(), { timeout: 3000 })
  })

  it('veri yokken yazdırmaz, hata gösterir', async () => {
    renderPage('9999')

    const alert = await screen.findByRole('alert', {}, { timeout: 3000 })
    expect(alert.textContent).toContain('Rezervasyon bulunamadı.')
    expect(printSpy).not.toHaveBeenCalled()
  })
})
