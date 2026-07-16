import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { HotelList } from '@/features/hotels/HotelList'
import type { HotelProduct } from '@/types'

afterEach(cleanup)

const makeHotels = (n: number): HotelProduct[] =>
  Array.from({ length: n }, (_, i) => ({
    id: `htl-${i}`,
    offerId: `off-htl-${i}`,
    hotelName: `Otel ${i}`,
    region: 'Antalya',
    stars: 5,
    price: 1000 + i,
    currency: 'EUR',
    boardType: 'AI',
    availability: true,
  }))

function renderList(products: HotelProduct[]) {
  const store = configureStore({ reducer: { reservationDraft: reservationDraftReducer } })
  render(
    <Provider store={store}>
      <MemoryRouter>
        <HotelList products={products} />
      </MemoryRouter>
    </Provider>,
  )
}

describe('HotelList — Daha fazla göster (madde 9)', () => {
  it('ilk 5 oteli gösterir, kalanı düğmeyle açar', async () => {
    const user = userEvent.setup()
    renderList(makeHotels(7))

    // İlk sayfa yalnız 5 kart (her kartta bir Seç düğmesi).
    expect(screen.getAllByRole('button', { name: /seç/i })).toHaveLength(5)
    expect(screen.queryByText('Otel 6')).toBeNull()

    // "Daha fazla göster (2)" → tümü görünür, düğme kaybolur.
    await user.click(screen.getByRole('button', { name: /daha fazla göster \(2\)/i }))
    expect(screen.getAllByRole('button', { name: /seç/i })).toHaveLength(7)
    expect(screen.getByText('Otel 6')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /daha fazla göster/i })).toBeNull()
  })

  it('5 veya daha az sonuçta düğme hiç çıkmaz', () => {
    renderList(makeHotels(4))
    expect(screen.getAllByRole('button', { name: /seç/i })).toHaveLength(4)
    expect(screen.queryByRole('button', { name: /daha fazla göster/i })).toBeNull()
  })

  it('boş listede açıklayıcı mesaj gösterir', () => {
    renderList([])
    expect(screen.getByText('Kriterlere uyan otel bulunamadı.')).toBeTruthy()
  })
})
