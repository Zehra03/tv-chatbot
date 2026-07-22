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
  it('tüm müşterilerin rezervasyonlarını hesap sahibiyle listeler', async () => {
    renderPage()

    // Üç fixture'ın üçü de görünür: liste hiçbir kullanıcıya göre daraltılmaz.
    expect(await screen.findByText('PAX-MOCK-1001')).toBeTruthy()
    expect(screen.getByText('PAX-MOCK-1002')).toBeTruthy()
    expect(screen.getByText('PAX-MOCK-1003')).toBeTruthy()

    // Üye rezervasyonunda hesap sahibinin e-postası görünür — "Hesap" sütununun tüm varlık
    // sebebi bu: hangi kayıtlı kullanıcıya ait olduğu yolcu adından okunamaz.
    expect(screen.getAllByText(/@example\.com$/).length).toBeGreaterThan(0)

    // Misafir fixture'ının (PAX-MOCK-1002) hesabı yoktur; e-posta yerine rozet çıkar.
    expect(screen.getByText('Misafir')).toBeTruthy()
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
