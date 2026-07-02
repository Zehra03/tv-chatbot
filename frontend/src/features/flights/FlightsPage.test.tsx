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

async function search(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nereden'), 'İstanbul')
  await user.type(screen.getByLabelText('Nereye'), 'Antalya')
  fireEvent.change(screen.getByLabelText('Gidiş tarihi'), { target: { value: '2026-08-01' } })
  await user.click(screen.getByRole('button', { name: 'Ara' }))
}

describe('FlightsPage (MSW ile)', () => {
  it('arar ve filtreler sonucu daraltır', async () => {
    const user = userEvent.setup()
    renderPage()

    // İstanbul → Antalya: 3 fixture uçuşu. ("TestJet" filtre <option>'ında da
    // geçtiğinden metin yerine sonuç sayısıyla doğrula.)
    await search(user)
    expect(await screen.findByText('3 sonuç', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getAllByText('TestJet').length).toBeGreaterThanOrEqual(2) // kart + option

    // Yalnızca direkt → 1 aktarmalı TestJet kartı elenir (option kalır).
    await user.click(screen.getByLabelText('Yalnızca direkt'))
    expect(screen.getByText('2 sonuç')).toBeTruthy()
    expect(screen.getAllByText('TestJet')).toHaveLength(1)

    // Havayolu filtresi listedeki havayollarından türetilir.
    await user.selectOptions(screen.getByLabelText('Havayolu filtresi'), 'MockAir')
    expect(screen.getByText('2 sonuç')).toBeTruthy()

    // Temizle → 3 sonuç geri gelir.
    await user.click(screen.getByRole('button', { name: 'Temizle' }))
    expect(screen.getByText('3 sonuç')).toBeTruthy()
  })

  it('Seç, uçuş taslağını yazıp rezervasyon formuna yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderPage()

    await search(user)
    expect(await screen.findByText('3 sonuç', {}, { timeout: 3000 })).toBeTruthy()

    // En ucuz uçuşu öne al, ilk Seç ona ait olsun.
    await user.selectOptions(screen.getByLabelText('Sıralama'), 'price-asc')
    await user.click(screen.getAllByRole('button', { name: 'Seç' })[0])
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    expect(store.getState().reservationDraft.draft).toMatchObject({
      productType: 'flight',
      productId: 'flt-mock-002',
    })
  })
})
