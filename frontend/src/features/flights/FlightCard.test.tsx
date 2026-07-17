import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { FlightCard } from '@/features/flights/FlightCard'
import type { FlightProduct } from '@/types'

afterEach(cleanup)

const oneWay: FlightProduct = {
  id: 'flt-1',
  offerId: 'offer-1',
  airline: 'TK',
  origin: 'AYT',
  destination: 'ADB',
  originCity: 'Antalya',
  destinationCity: 'Izmir',
  departTime: '2026-07-18T06:00:00Z',
  arriveTime: '2026-07-18T07:10:00Z',
  tripType: 'one_way',
  returnDepartTime: null,
  returnArriveTime: null,
  stops: 0,
  baggage: '20kg',
  price: 1500,
  currency: 'TRY',
}

/** The provider sells this AYT⇄ADB trip as one ticket: both legs, one price, one token. */
const roundTrip: FlightProduct = {
  ...oneWay,
  id: 'flt-2',
  tripType: 'round_trip',
  returnDepartTime: '2026-07-21T15:00:00Z',
  returnArriveTime: '2026-07-21T16:10:00Z',
  returnAirline: 'PC',
  returnStops: 1,
  price: 3817,
}

function renderCard(product: FlightProduct) {
  const store = configureStore({ reducer: { reservationDraft: reservationDraftReducer } })
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/flights']}>
        <Routes>
          <Route path="/flights" element={<FlightCard product={product} />} />
          <Route path="/reservation/new" element={<div>REZERVASYON FORMU STUB</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  )
}

describe('FlightCard', () => {
  it('gidiş-dönüşte iki bacağı da ayrı satır olarak gösterir', () => {
    renderCard(roundTrip)

    expect(screen.getByText('Gidiş')).toBeTruthy()
    expect(screen.getByText('Dönüş')).toBeTruthy()
    // Bacaklar ters yönde: AYT hem kalkış hem varış olarak bir kez geçer.
    expect(screen.getAllByText('AYT')).toHaveLength(2)
    expect(screen.getAllByText('ADB')).toHaveLength(2)
  })

  it('dönüş bacağının kendi havayolunu ve aktarmasını gösterir', () => {
    renderCard(roundTrip)

    expect(screen.getByText('PC')).toBeTruthy() // dönüşü başka havayolu uçuruyor
    expect(screen.getByText('Direkt')).toBeTruthy() // gidiş
    expect(screen.getByText('1 aktarma')).toBeTruthy() // dönüş
  })

  it('fiyatı gidiş-dönüş toplamı olarak etiketler', () => {
    renderCard(roundTrip)

    // Tek bacağın yanındaki rakam tek yön fiyatı sanılmamalı.
    expect(screen.getByText('gidiş-dönüş toplamı')).toBeTruthy()
  })

  it('tek yönde bacak etiketi ve toplam notu çıkarmaz', () => {
    renderCard(oneWay)

    expect(screen.queryByText('Gidiş')).toBeNull()
    expect(screen.queryByText('Dönüş')).toBeNull()
    expect(screen.queryByText('gidiş-dönüş toplamı')).toBeNull()
  })

  /**
   * A round-trip search with no return leg still answers with outbound-only cards; the card must
   * not claim a return it cannot show (the same rule the backend applies in FlightProductApiDto).
   */
  it('dönüş bacağı yoksa gidiş-dönüş gibi göstermez', () => {
    renderCard({ ...roundTrip, returnDepartTime: null, returnArriveTime: null })

    expect(screen.queryByText('Dönüş')).toBeNull()
    expect(screen.queryByText('gidiş-dönüş toplamı')).toBeNull()
  })
})
