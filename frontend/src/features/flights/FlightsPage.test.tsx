import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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
import { FlightsPage } from '@/features/flights/FlightsPage'

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
        <MemoryRouter initialEntries={['/flights']}>
          <Routes>
            <Route path="/flights" element={<FlightsPage />} />
            <Route path="/reservation/new" element={<div>REZERVASYON FORMU STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return { store }
}

// Konum alanı artık yalnızca dropdown önerisinden seçilebilir: yaz → öneriyi bekle → tıkla.
async function selectLocation(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  text: string,
  optionPattern: RegExp,
) {
  await user.type(screen.getByLabelText(label), text)
  const option = await screen.findByRole('option', { name: optionPattern }, { timeout: 3000 })
  await user.click(option)
}

async function search(user: ReturnType<typeof userEvent.setup>) {
  await selectLocation(user, 'Nereden', 'İstanbul', /İstanbul Havalimanı/)
  await selectLocation(user, 'Nereye', 'Antalya', /Antalya Havalimanı/)
  fireEvent.change(screen.getByLabelText('Gidiş tarihi'), { target: { value: '2026-08-01' } })
  await user.click(screen.getByRole('button', { name: 'Ara' }))
}

describe('FlightsPage (MSW ile)', () => {
  it('arar ve filtreler sonucu daraltır', async () => {
    const user = userEvent.setup()
    renderPage()

    // İstanbul → Antalya: 3 fixture uçuşu. (DropdownSelect seçenekleri yalnızca
    // menü açıkken DOM'da olduğundan "TestJet" sadece kartta görünür.)
    await search(user)
    expect(await screen.findByText('3 sonuç', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getAllByText('TestJet')).toHaveLength(1) // kart

    // Yalnızca direkt → 1 aktarmalı TestJet kartı elenir.
    await user.click(screen.getByLabelText('Yalnızca direkt'))
    expect(screen.getByText('2 sonuç')).toBeTruthy()
    expect(screen.queryByText('TestJet')).toBeNull()

    // Havayolu filtresi listedeki havayollarından türetilir
    // (DropdownSelect listbox deseni: tetikleyiciye tıkla, seçeneğe tıkla).
    await user.click(screen.getByLabelText('Havayolu filtresi'))
    await user.click(await screen.findByRole('option', { name: 'MockAir' }))
    expect(screen.getByText('2 sonuç')).toBeTruthy()

    // Temizle → 3 sonuç geri gelir.
    await user.click(screen.getByRole('button', { name: 'Temizle' }))
    expect(screen.getByText('3 sonuç')).toBeTruthy()
  })

  it('konum otomatik tamamlamadan öneri seçince alanı doldurur', async () => {
    const user = userEvent.setup()
    renderPage()

    // "Anta" yaz → backend (MSW) TourVisio önerilerini döndürür → listeden seç.
    await user.type(screen.getByLabelText('Nereye'), 'Anta')
    const option = await screen.findByRole(
      'option',
      { name: /Antalya Havalimanı/ },
      { timeout: 3000 },
    )
    await user.click(option)

    expect((screen.getByLabelText('Nereye') as HTMLInputElement).value).toContain(
      'Antalya Havalimanı',
    )
    // Seçimden sonra liste kapanır.
    expect(screen.queryByRole('option', { name: /Antalya Havalimanı/ })).toBeNull()
  })

  it('Seç, uçuş taslağını yazıp rezervasyon formuna yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderPage()

    await search(user)
    expect(await screen.findByText('3 sonuç', {}, { timeout: 3000 })).toBeTruthy()

    // En ucuz uçuşu öne al, ilk Seç ona ait olsun.
    await user.click(screen.getByLabelText('Sıralama'))
    await user.click(await screen.findByRole('option', { name: 'Fiyat (artan)' }))
    await user.click(screen.getAllByRole('button', { name: /seç/i })[0])
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    // Taslak, arama-satırı id'sini (flt-mock-002) değil TourVisio teklif jetonunu taşımalı —
    // booking `id` ile yapılırsa "offer no longer bookable" hatası verir (bkz. buildFlightDraft).
    expect(store.getState().reservationDraft.draft).toMatchObject({
      productType: 'flight',
      offerId: 'offer-mock-002',
    })
  })

  it('listeden seçmeden serbest metinle arama yapmaz, uyarı gösterir', async () => {
    const user = userEvent.setup()
    renderPage()

    // Öneri açılır ama seçilmez → serbest metin aramaya gitmemeli.
    await user.type(screen.getByLabelText('Nereden'), 'İstanbul')
    await user.type(screen.getByLabelText('Nereye'), 'Antalya')
    fireEvent.change(screen.getByLabelText('Gidiş tarihi'), { target: { value: '2026-08-01' } })
    await user.click(screen.getByRole('button', { name: 'Ara' }))

    expect(await screen.findByText(/kalkış yerini listeden seçin/i)).toBeTruthy()
    expect(screen.queryByText(/\d+ sonuç/)).toBeNull()
  })

  it('geçmiş gidiş tarihinde uyarı gösterir ve arama yapmaz', async () => {
    const user = userEvent.setup()
    renderPage()

    await selectLocation(user, 'Nereden', 'İstanbul', /İstanbul Havalimanı/)
    await selectLocation(user, 'Nereye', 'Antalya', /Antalya Havalimanı/)
    fireEvent.change(screen.getByLabelText('Gidiş tarihi'), { target: { value: '2020-01-01' } })
    await user.click(screen.getByRole('button', { name: 'Ara' }))

    expect(await screen.findByText(/gidiş tarihi geçmişte olamaz/i)).toBeTruthy()
    // Geçersiz kriterde arama tetiklenmez → "N sonuç" satırı görünmez.
    expect(screen.queryByText(/\d+ sonuç/)).toBeNull()
  })

  it('kalkış ve varış aynıysa uyarı gösterir', async () => {
    const user = userEvent.setup()
    renderPage()

    // İki alanda da aynı konumu (Antalya) listeden seç → kalkış = varış.
    await selectLocation(user, 'Nereden', 'Antalya', /Antalya Havalimanı/)
    await selectLocation(user, 'Nereye', 'Antalya', /Antalya Havalimanı/)
    fireEvent.change(screen.getByLabelText('Gidiş tarihi'), { target: { value: '2026-08-01' } })
    await user.click(screen.getByRole('button', { name: 'Ara' }))

    expect(await screen.findByText(/aynı olamaz/i)).toBeTruthy()
    expect(screen.queryByText(/\d+ sonuç/)).toBeNull()
  })

  it('gidiş-dönüşte dönüş tarihi gidişten önceyse uyarı gösterir', async () => {
    const user = userEvent.setup()
    renderPage()

    await selectLocation(user, 'Nereden', 'İstanbul', /İstanbul Havalimanı/)
    await selectLocation(user, 'Nereye', 'Antalya', /Antalya Havalimanı/)
    // Yön → Gidiş-dönüş (DropdownSelect listbox: tetikleyiciye tıkla, seçeneğe tıkla).
    await user.click(screen.getByLabelText('Yön'))
    await user.click(await screen.findByRole('option', { name: 'Gidiş-dönüş' }))
    fireEvent.change(screen.getByLabelText('Gidiş tarihi'), { target: { value: '2026-08-10' } })
    fireEvent.change(screen.getByLabelText('Dönüş tarihi'), { target: { value: '2026-08-05' } })
    await user.click(screen.getByRole('button', { name: 'Ara' }))

    expect(await screen.findByText(/dönüş tarihi gidiş tarihinden önce olamaz/i)).toBeTruthy()
    expect(screen.queryByText(/\d+ sonuç/)).toBeNull()
  })
})
