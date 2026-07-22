import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { AdminReservationsPage } from '@/features/admin/AdminReservationsPage'

/**
 * Toast'lar <Toaster /> ile providers.tsx'te mount edilir; sayfa testinde o kabuk yok, dolayısıyla
 * bildirimler DOM'a hiç düşmez. Sonucun NASIL bildirildiğini doğrulamak istediğimiz için çağrıları
 * doğrudan gözlüyoruz — "202 başarı sayılmasın" kuralı tam olarak burada yaşıyor.
 */
// vi.hoisted: vi.mock dosyanın tepesine kaldırılır, sıradan bir const ondan SONRA tanımlanacağı
// için fabrika içinde erişilemez ("Cannot access before initialization").
const toastMock = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
}))
vi.mock('sonner', () => ({ toast: toastMock }))

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.clearAllMocks()
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
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/admin/reservations']}>
          <AdminReservationsPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
}

describe('AdminReservationsPage', () => {
  it('rezervasyonları listeler ve misafir rezervasyonu işaretler', async () => {
    renderPage()

    expect(await screen.findByText('PAX-MOCK-1001')).toBeTruthy()
    // Misafir fixture'ı (PAX-MOCK-1002) "Misafir" rozetiyle ayrılır — üye/misafir ayrımı
    // yalnızca backend'in guest bayrağından gelir, satırdan türetilmez.
    expect(await screen.findByText('Misafir')).toBeTruthy()
    expect(screen.getAllByText('Üye').length).toBeGreaterThan(0)
  })

  /**
   * PNR araması SUNUCUDA filtrelenmeli: liste sayfalıdır, istemcide filtrelemek yalnızca açık
   * sayfadaki satırları arardı ve sonraki sayfalardaki eşleşme hiç bulunamazdı.
   */
  it('PNR aramasını q parametresiyle sunucuya gönderir', async () => {
    const seen: string[] = []
    server.use(
      http.get('/api/v1/admin/reservations', ({ request }) => {
        const url = new URL(request.url)
        seen.push(url.searchParams.get('q') ?? '')
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: 0,
          size: 20,
        })
      }),
    )

    renderPage()
    await userEvent.type(screen.getByLabelText('PNR ile ara'), 'MOCK-1002')

    await waitFor(() => expect(seen).toContain('MOCK-1002'))
  })

  /**
   * 202 CANCEL_OUTCOME_UNKNOWN "iptal edildi" DEMEK DEĞİLDİR: backend TourVisio'dan yanıt
   * alamadığını söylüyor. Bu dalda yeşil onay göstermek, yöneticiyi iptal olduğuna inandırıp
   * takibi bıraktırırdı — canlı bir rezervasyon iptal edilmiş sanılır.
   */
  it('belirsiz iptal sonucunu (202) başarı gibi göstermez', async () => {
    server.use(
      http.put('/api/v1/admin/reservations/:id/status', () =>
        HttpResponse.json(
          {
            outcome: 'CANCEL_OUTCOME_UNKNOWN',
            message: 'İptal talebi alındı ancak sonucu belirsiz.',
          },
          { status: 202 },
        ),
      ),
    )

    renderPage()
    const cancelButtons = await screen.findAllByRole('button', { name: 'İptal et' })
    await userEvent.click(cancelButtons[0])

    await userEvent.type(screen.getByLabelText('İptal sebebi'), 'müşteri talebi')
    await userEvent.click(screen.getByRole('button', { name: 'Rezervasyonu iptal et' }))

    // Belirsiz dalda backend'in kendi uyarı metni gösterilir, "Rezervasyon iptal edildi." değil.
    await waitFor(() =>
      expect(toastMock.warning).toHaveBeenCalledWith(
        'İptal talebi alındı ancak sonucu belirsiz.',
        expect.anything(),
      ),
    )
    expect(toastMock.success).not.toHaveBeenCalled()
  })
})
