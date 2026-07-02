import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer, {
  type ReservationDraft,
} from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { ReservationFormPage } from '@/features/reservation/ReservationFormPage'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const hotelDraft: ReservationDraft = {
  productType: 'hotel',
  productId: 'htl-mock-001',
  title: 'MOCK Grand Antalya Resort',
  summary: 'Antalya · 5★ · AI',
  price: 1200,
  currency: 'EUR',
}

function renderPage(draft: ReservationDraft | null = hotelDraft) {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      reservationDraft: reservationDraftReducer,
      ui: uiReducer,
    },
    preloadedState: { reservationDraft: { draft } },
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/reservation/new']}>
          <Routes>
            <Route path="/reservation/new" element={<ReservationFormPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return { store, queryClient }
}

describe('ReservationFormPage', () => {
  it('taslak yokken yönlendirme mesajı gösterir', () => {
    renderPage(null)
    expect(screen.getByText(/Önce bir ürün seçmelisiniz/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Sohbete git' })).toBeTruthy()
  })

  it('ürün özeti taslaktan gelir', () => {
    renderPage()
    expect(screen.getByText('MOCK Grand Antalya Resort')).toBeTruthy()
    expect(screen.getByText('Antalya · 5★ · AI')).toBeTruthy()
    expect(screen.getByText(/1\.200/)).toBeTruthy()
  })

  it('boş gönderimde Zod hataları gösterilir', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findAllByText('En az 2 karakter girin')).toHaveLength(2)
    expect(screen.getByText('Geçerli bir e-posta girin')).toBeTruthy()
    expect(screen.getByText('Geçerli bir telefon girin')).toBeTruthy()
  })

  it('geçersiz yaş ve uyruk alanları hata verir', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Yaş (opsiyonel)'), '200')
    await user.type(screen.getByLabelText('Uyruk (opsiyonel)'), '1')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findByText('Geçerli bir yaş girin (0–120)')).toBeTruthy()
    expect(screen.getByText('İki harfli ülke kodu girin (ör. TR)')).toBeTruthy()
  })

  it('geçerli form → önizleme; onay checkbox işaretlenmeden gönderilemez, işaretlenince POST atılır', async () => {
    const requests: string[] = []
    server.events.on('request:start', ({ request }) => {
      requests.push(`${request.method} ${new URL(request.url).pathname}`)
    })

    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Ad'), 'Zehra')
    await user.type(screen.getByLabelText('Soyad'), 'Yılmaz')
    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Telefon'), '+905551112233')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))

    // Önizleme: backend'in hesapladığı toplam + misafir listesi.
    expect(await screen.findByText(/Toplam:/, {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText(/1\.200/)).toBeTruthy()
    expect(screen.getByText(/Zehra Yılmaz — Yetişkin/)).toBeTruthy()

    // Açık onay: checkbox işaretlenmeden buton devre dışı.
    const submit = screen.getByRole('button', { name: 'Rezervasyonu onayla' })
    expect((submit as HTMLButtonElement).disabled).toBe(true)
    await user.click(screen.getByRole('checkbox'))
    expect((submit as HTMLButtonElement).disabled).toBe(false)

    await user.click(submit)
    await waitFor(() => expect(requests).toContain('POST /api/v1/reservations'))
    server.events.removeAllListeners()
  })

  async function fillAndPreview(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Ad'), 'Zehra')
    await user.type(screen.getByLabelText('Soyad'), 'Yılmaz')
    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Telefon'), '+905551112233')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    await screen.findByText(/Toplam:/, {}, { timeout: 3000 })
  }

  it('başarıda sonuç ekranı gösterilir, liste invalidate edilir, taslak temizlenir', async () => {
    const user = userEvent.setup()
    const { store, queryClient } = renderPage()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    await fillAndPreview(user)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Rezervasyonu onayla' }))

    // Başarı ekranı: numara + durum + detay/liste bağlantıları.
    expect(await screen.findByText('Rezervasyonunuz alındı', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText(/PAX-MOCK-\d+/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Detayı gör' })).toBeTruthy()

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reservations'] })
    expect(store.getState().reservationDraft.draft).toBeNull()
  })

  it('backend hatasında başarısızlık ekranı gösterilir', async () => {
    server.use(
      http.post('/api/v1/reservations', () =>
        HttpResponse.json({ message: 'Kontenjan kalmadı' }, { status: 409 }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await fillAndPreview(user)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Rezervasyonu onayla' }))

    expect(await screen.findByText('Rezervasyon oluşturulamadı', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('Kontenjan kalmadı')
    expect(screen.getByRole('button', { name: 'Önizlemeye dön' })).toBeTruthy()
  })
})
