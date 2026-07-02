import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { HotelsPage } from '@/features/hotels/HotelsPage'

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
        <MemoryRouter initialEntries={['/hotels']}>
          <Routes>
            <Route path="/hotels" element={<HotelsPage />} />
            <Route path="/reservation/new" element={<div>REZERVASYON FORMU STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return { store }
}

async function search(user: ReturnType<typeof userEvent.setup>, destination: string) {
  await user.type(screen.getByLabelText('Nereye'), destination)
  fireEvent.change(screen.getByLabelText('Giriş'), { target: { value: '2026-08-01' } })
  fireEvent.change(screen.getByLabelText('Çıkış'), { target: { value: '2026-08-05' } })
  await user.click(screen.getByRole('button', { name: 'Ara' }))
}

describe('HotelsPage (MSW ile)', () => {
  it('arar, filtreler sonucu daraltır, sıfır sonuçta boş mesaj gösterir', async () => {
    const user = userEvent.setup()
    renderPage()

    // Mock, eşleşmeyen bölgede tüm fixture'ları döndürür → 5 sonuç.
    await search(user, 'Mars')
    expect(await screen.findByText('MOCK Grand Antalya Resort', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('5 sonuç')).toBeTruthy()

    // minStars=5 → 3★ Sample Boutique elenir, 2 sonuç kalır.
    await user.selectOptions(screen.getByLabelText('Yıldız filtresi'), '5')
    expect(screen.getByText('2 sonuç')).toBeTruthy()
    expect(screen.queryByText('Sample Boutique Kapadokya')).toBeNull()

    // maxPrice=1300 → 1500'lük MOCK Palace da elenir.
    await user.type(screen.getByLabelText('En yüksek fiyat'), '1300')
    expect(screen.getByText('1 sonuç')).toBeTruthy()
    expect(screen.queryByText('MOCK Palace İzmir')).toBeNull()

    // İmkânsız fiyat → boş liste mesajı.
    await user.clear(screen.getByLabelText('En yüksek fiyat'))
    await user.type(screen.getByLabelText('En yüksek fiyat'), '10')
    expect(screen.getByText('Kriterlere uyan otel bulunamadı.')).toBeTruthy()

    // Temizle → yine 5 sonuç.
    await user.click(screen.getByRole('button', { name: 'Temizle' }))
    expect(screen.getByText('5 sonuç')).toBeTruthy()
  })

  it('Seç, taslağı yazıp rezervasyon formuna yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderPage()

    await search(user, 'Antalya')
    expect(await screen.findByText('MOCK Grand Antalya Resort', {}, { timeout: 3000 })).toBeTruthy()

    await user.click(screen.getByRole('button', { name: /seç/i }))
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    expect(store.getState().reservationDraft.draft).toMatchObject({
      productType: 'hotel',
      productId: 'htl-mock-001',
    })
  })

  it('sunucu hatasında hata mesajı ve tekrar dene gösterir', async () => {
    server.use(
      http.post('/api/v1/hotels/search', () =>
        HttpResponse.json({ message: 'Sunucu hatası' }, { status: 500 }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await search(user, 'Antalya')
    const alert = await screen.findByRole('alert', {}, { timeout: 3000 })
    expect(alert.textContent).toContain('Sunucu hatası')
    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeTruthy()
  })
})
