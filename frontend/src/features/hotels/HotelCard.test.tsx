import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { HotelCard } from '@/features/hotels/HotelCard'
import type { HotelProduct, HotelSearchCriteria } from '@/types'

afterEach(cleanup)

const hotel: HotelProduct = {
  id: 'htl-mock-001',
  offerId: 'off-htl-mock-001',
  hotelName: 'MOCK Grand Antalya Resort',
  region: 'Antalya',
  stars: 5,
  price: 1200,
  currency: 'EUR',
  boardType: 'AI',
  availability: true,
}

// "Seç"in rezervasyon snapshot'ını kurabilmesi için arama kriteri (giriş/çıkış, oda/kişi).
const criteria: Partial<HotelSearchCriteria> = {
  checkIn: '2026-07-10',
  checkOut: '2026-07-17',
  adults: 2,
  children: 0,
  rooms: 1,
  nationality: 'TR',
}

function renderCard(product: HotelProduct, cardCriteria: Partial<HotelSearchCriteria> | undefined = criteria) {
  const store = configureStore({ reducer: { reservationDraft: reservationDraftReducer } })
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/hotels']}>
        <Routes>
          <Route path="/hotels" element={<HotelCard product={product} criteria={cardCriteria} />} />
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
    // Yıldız artık paylaşılan StarRating ile (detay sayfasıyla tek dil, §4): metin değil,
    // role="img" + aria-label taşır.
    expect(screen.getByLabelText('5 yıldız')).toBeTruthy()
    // Pansiyon rozeti ham "AI" değil, boardBadgeLabel ile temiz etiket gösterir.
    expect(screen.getByText('Herşey Dahil')).toBeTruthy()
    expect(screen.getByText(/1\.200/)).toBeTruthy()
  })

  it('image verildiğinde otel görselini render eder', () => {
    renderCard({ ...hotel, image: 'https://example.test/otel.jpg' })
    const img = screen.getByRole('img', { name: /otel görseli/i }) as HTMLImageElement
    expect(img.src).toBe('https://example.test/otel.jpg')
  })

  it('image yoksa görsel yerine placeholder gösterir', () => {
    renderCard({ ...hotel, image: null })
    // StarRating da role="img" taşır; sorguyu otel görseline daralt (yalnız otel fotoğrafı yok).
    expect(screen.queryByRole('img', { name: /otel görseli/i })).toBeNull()
  })

  it('müsait olmayan otelde rozet gösterir ve Seç devre dışıdır', () => {
    renderCard({ ...hotel, availability: false })
    expect(screen.getByText('Müsait değil')).toBeTruthy()
    expect((screen.getByRole('button', { name: /seç/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('Seç, ürün + kriterden tam snapshot taslağı yazar ve rezervasyon formuna yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderCard(hotel)

    await user.click(screen.getByRole('button', { name: /seç/i }))
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    expect(store.getState().reservationDraft.draft).toEqual({
      productType: 'hotel',
      offerId: 'off-htl-mock-001',
      title: 'MOCK Grand Antalya Resort',
      summary: 'Antalya · 5★ · AI',
      price: 1200,
      currency: 'EUR',
      childAges: [],
      hotel: {
        hotelName: 'MOCK Grand Antalya Resort',
        region: 'Antalya',
        stars: 5,
        boardType: 'AI',
        checkIn: '2026-07-10',
        checkOut: '2026-07-17',
        rooms: 1,
        adults: 2,
        children: 0,
        nationality: 'TR',
        price: 1200,
        currency: 'EUR',
      },
    })
  })

  it('kriter eksikse (giriş/çıkış/yetişkin yok) Seç devre dışıdır', () => {
    renderCard(hotel, {})
    expect((screen.getByRole('button', { name: /seç/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('kartın gövdesine (Seç dışında) tıklamak da seçer ve forma yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderCard(hotel)

    // Küçük Seç düğmesine değil, kart gövdesindeki otel adına tıkla — tüm kart tıklanabilir.
    await user.click(screen.getByText('MOCK Grand Antalya Resort'))
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    expect(store.getState().reservationDraft.draft).toMatchObject({
      productType: 'hotel',
      offerId: 'off-htl-mock-001',
    })
  })

  it('seçilemeyen kartta (kriter eksik) gövde tıklaması yönlendirmez', async () => {
    const user = userEvent.setup()
    const { store } = renderCard(hotel, {})

    await user.click(screen.getByText('MOCK Grand Antalya Resort'))
    // Ne yönlendirme ne taslak — kart hâlâ ekranda.
    expect(screen.queryByText('REZERVASYON FORMU STUB')).toBeNull()
    expect(store.getState().reservationDraft.draft).toBeFalsy()
    expect(screen.getByText('MOCK Grand Antalya Resort')).toBeTruthy()
  })
})
