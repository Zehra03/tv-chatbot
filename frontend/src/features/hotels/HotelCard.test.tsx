import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { HotelCard } from '@/features/hotels/HotelCard'
import type { HotelProduct } from '@/types'

afterEach(cleanup)

const hotel: HotelProduct = {
  id: 'htl-mock-001',
  hotelName: 'MOCK Grand Antalya Resort',
  region: 'Antalya',
  stars: 5,
  price: 1200,
  currency: 'EUR',
  boardType: 'AI',
  availability: true,
}

function renderCard(product: HotelProduct) {
  const store = configureStore({ reducer: { reservationDraft: reservationDraftReducer } })
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/hotels']}>
        <Routes>
          <Route path="/hotels" element={<HotelCard product={product} />} />
          <Route path="/reservation/new" element={<div>REZERVASYON FORMU STUB</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  )
  return { store }
}

describe('HotelCard', () => {
  it('otel alanlarını render eder (ad, bölge, yıldız, pansiyon, fiyat)', () => {
    renderCard(hotel)
    expect(screen.getByText('MOCK Grand Antalya Resort')).toBeTruthy()
    expect(screen.getByText('Antalya')).toBeTruthy()
    expect(screen.getByText('5')).toBeTruthy()
    expect(screen.getByText('AI')).toBeTruthy()
    expect(screen.getByText(/1\.200/)).toBeTruthy()
  })

  it('image verildiğinde otel görselini render eder', () => {
    renderCard({ ...hotel, image: 'https://example.test/otel.jpg' })
    const img = screen.getByRole('img', { name: /otel görseli/i }) as HTMLImageElement
    expect(img.src).toBe('https://example.test/otel.jpg')
  })

  it('image yoksa görsel yerine placeholder gösterir', () => {
    renderCard({ ...hotel, image: null })
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('müsait olmayan otelde rozet gösterir ve Seç devre dışıdır', () => {
    renderCard({ ...hotel, availability: false })
    expect(screen.getByText('Müsait değil')).toBeTruthy()
    expect((screen.getByRole('button', { name: /seç/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('Seç, normalize taslağı yazar ve rezervasyon formuna yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderCard(hotel)

    await user.click(screen.getByRole('button', { name: /seç/i }))
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    expect(store.getState().reservationDraft.draft).toEqual({
      productType: 'hotel',
      productId: 'htl-mock-001',
      title: 'MOCK Grand Antalya Resort',
      summary: 'Antalya · 5★ · AI',
      price: 1200,
      currency: 'EUR',
    })
  })
})
