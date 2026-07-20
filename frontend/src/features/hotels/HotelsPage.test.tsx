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

// Destinasyon artık yalnızca dropdown önerisinden seçilebilir: yaz → öneriyi bekle
// → tıkla. optionPattern verilmezse yazılan metnin tam eşleşmesi aranır.
async function search(
  user: ReturnType<typeof userEvent.setup>,
  destination: string,
  optionPattern: RegExp = new RegExp(`^${destination}$`),
) {
  await user.type(screen.getByLabelText('Nereye'), destination)
  const option = await screen.findByRole('option', { name: optionPattern }, { timeout: 3000 })
  await user.click(option)
  fireEvent.change(screen.getByLabelText('Giriş'), { target: { value: '2026-08-01' } })
  fireEvent.change(screen.getByLabelText('Çıkış'), { target: { value: '2026-08-05' } })
  await user.click(screen.getByRole('button', { name: 'Ara' }))
}

describe('HotelsPage (MSW ile)', () => {
  it('arar, filtreler sonucu daraltır, sıfır sonuçta boş mesaj gösterir', async () => {
    const user = userEvent.setup()
    renderPage()

    // Bursa listede var ama otel fixture'ı yok → mock eşleşmeyen bölgede tüm
    // fixture'ları döndürür → 5 sonuç (filtreleri denemek için tüm liste gerekir).
    await search(user, 'Bursa')
    expect(await screen.findByText('MOCK Grand Antalya Resort', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('5 sonuç')).toBeTruthy()

    // minStars=5 → 3★ Sample Boutique elenir, 2 sonuç kalır.
    // (DropdownSelect listbox deseni: tetikleyiciye tıkla, seçeneğe tıkla.)
    await user.click(screen.getByLabelText('Yıldız filtresi'))
    await user.click(await screen.findByRole('option', { name: '5★' }))
    expect(screen.getByText('2 sonuç')).toBeTruthy()
    expect(screen.queryByText('Sample Boutique Kapadokya')).toBeNull()

    // maxPrice=1300 → 1500'lük MOCK Palace da elenir.
    await user.type(screen.getByLabelText('En yüksek fiyat'), '1300')
    expect(screen.getByText('1 sonuç')).toBeTruthy()
    expect(screen.queryByText('MOCK Palace İzmir')).toBeNull()

    // İmkânsız fiyat → filtreler tüm sonuçları eler → pasif mesaj değil, tıklanabilir
    // kurtarma aksiyonu (§6): "Filtrelerinizle eşleşen otel yok" + "Filtreleri temizle".
    await user.clear(screen.getByLabelText('En yüksek fiyat'))
    await user.type(screen.getByLabelText('En yüksek fiyat'), '10')
    expect(screen.getByText('Filtrelerinizle eşleşen otel yok')).toBeTruthy()

    // Kurtarma butonu tüm filtreleri sıfırlar → yine 5 sonuç.
    await user.click(screen.getByRole('button', { name: 'Filtreleri temizle' }))
    expect(screen.getByText('5 sonuç')).toBeTruthy()
  })

  it('destination otomatik tamamlamadan öneri seçince alanı doldurur', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Nereye'), 'Anta')
    const option = await screen.findByRole('option', { name: /Antalya/ }, { timeout: 3000 })
    await user.click(option)

    expect((screen.getByLabelText('Nereye') as HTMLInputElement).value).toBe('Antalya')
    expect(screen.queryByRole('option', { name: /Antalya/ })).toBeNull()
  })

  it('listeden seçmeden serbest metinle arama yapmaz, uyarı gösterir', async () => {
    const user = userEvent.setup()
    renderPage()

    // Öneri açılır ama seçilmez → serbest metin aramaya gitmemeli.
    await user.type(screen.getByLabelText('Nereye'), 'Antalya')
    fireEvent.change(screen.getByLabelText('Giriş'), { target: { value: '2026-08-01' } })
    fireEvent.change(screen.getByLabelText('Çıkış'), { target: { value: '2026-08-05' } })
    await user.click(screen.getByRole('button', { name: 'Ara' }))

    expect(await screen.findByText(/varış yerini listeden seçin/i)).toBeTruthy()
    // Geçersiz kriterde arama tetiklenmez → "N sonuç" satırı görünmez.
    expect(screen.queryByText(/\d+ sonuç/)).toBeNull()
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
      offerId: 'off-htl-mock-001',
    })
  })

  it('oda sayısı yetişkin sayısını aşamaz (her odada en az bir yetişkin)', async () => {
    const user = userEvent.setup()
    renderPage()

    // Misafir & oda popover'ını aç (varsayılan 2 yetişkin, 1 oda).
    await user.click(screen.getByLabelText('Misafir ve oda'))
    expect(screen.getByText('2 yetişkin, 1 oda')).toBeTruthy()

    // Oda 2'ye çıkınca yetişkine eşitlenir → "Oda artır" devre dışı (max = adults).
    await user.click(screen.getByRole('button', { name: 'Oda sayısını artır' }))
    expect(screen.getByText('2 yetişkin, 2 oda')).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Oda sayısını artır' }) as HTMLButtonElement).disabled).toBe(
      true,
    )

    // Yetişkin 1'e inince oda da 1'e kısılır (geçersiz 1 yetişkin / 2 oda önlenir).
    await user.click(screen.getByRole('button', { name: 'Yetişkin sayısını azalt' }))
    expect(screen.getByText('1 yetişkin, 1 oda')).toBeTruthy()
  })

  it('çocuk sayısı değişince yaş select sayısı eşleşir, girilen yaşlar korunur', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByLabelText('Misafir ve oda'))
    // Çocuk 0 iken yaş bölümü hiç render edilmez.
    expect(screen.queryByText(/Çocuk yaşları/)).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Çocuk sayısını artır' }))
    await user.click(screen.getByRole('button', { name: 'Çocuk sayısını artır' }))
    expect(screen.getByText('2 yetişkin, 2 çocuk, 1 oda')).toBeTruthy()

    // childAges.length === childCount invariantı (types/search.ts); varsayılan 7.
    const ages = () => screen.getAllByRole('combobox').filter((el) => el.tagName === 'SELECT')
    expect(ages()).toHaveLength(2)
    expect((screen.getByLabelText('1. çocuğun yaşı') as HTMLSelectElement).value).toBe('7')

    await user.selectOptions(screen.getByLabelText('1. çocuğun yaşı'), '10')
    expect((screen.getByLabelText('1. çocuğun yaşı') as HTMLSelectElement).value).toBe('10')

    // Azaltma sondan kırpar → 1. çocuğun girilen yaşı korunur.
    await user.click(screen.getByRole('button', { name: 'Çocuk sayısını azalt' }))
    expect(ages()).toHaveLength(1)
    expect((screen.getByLabelText('1. çocuğun yaşı') as HTMLSelectElement).value).toBe('10')

    // Tekrar artırınca yeni çocuk varsayılana döner, eskisi bozulmaz.
    await user.click(screen.getByRole('button', { name: 'Çocuk sayısını artır' }))
    expect((screen.getByLabelText('1. çocuğun yaşı') as HTMLSelectElement).value).toBe('10')
    expect((screen.getByLabelText('2. çocuğun yaşı') as HTMLSelectElement).value).toBe('7')

    // Sıfıra dönünce bölüm tümden kalkar.
    await user.click(screen.getByRole('button', { name: 'Çocuk sayısını azalt' }))
    await user.click(screen.getByRole('button', { name: 'Çocuk sayısını azalt' }))
    expect(screen.queryByText(/Çocuk yaşları/)).toBeNull()
    expect(screen.getByText('2 yetişkin, 1 oda')).toBeTruthy()
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
