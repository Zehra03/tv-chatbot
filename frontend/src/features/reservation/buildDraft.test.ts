import { describe, expect, it } from 'vitest'
import { buildFlightDraft, buildHotelDraft } from '@/features/reservation/buildDraft'
import type { FlightProduct, HotelProduct } from '@/types'

const hotel: HotelProduct = {
  id: 'htl-1',
  offerId: 'off-htl-1',
  hotelName: 'Test Hotel',
  region: 'Antalya',
  stars: 5,
  price: 1200,
  currency: 'EUR',
  boardType: 'AI',
  availability: true,
}

const flight: FlightProduct = {
  id: 'off-flt-1',
  offerId: 'offer-flt-1',
  airline: 'VF',
  origin: 'SAW',
  destination: 'AYT',
  departTime: '2026-08-01T08:30:00Z',
  arriveTime: '2026-08-01T09:45:00Z',
  tripType: 'one_way',
  returnDepartTime: null,
  returnArriveTime: null,
  stops: 0,
  baggage: '20kg',
  price: 120,
  currency: 'EUR',
}

describe('buildHotelDraft', () => {
  it('zorunlu kriter varken tam snapshot kurar', () => {
    const draft = buildHotelDraft(hotel, {
      checkIn: '2026-08-01',
      checkOut: '2026-08-05',
      adults: 2,
      children: 0,
      rooms: 1,
      nationality: 'TR',
    })
    expect(draft).not.toBeNull()
    expect(draft?.offerId).toBe('off-htl-1')
    expect(draft?.hotel.checkIn).toBe('2026-08-01')
    expect(draft?.hotel.rooms).toBe(1)
  })

  it('rooms eksikse 1e düşer, tarih/yetişkin eksikse null döner', () => {
    expect(
      buildHotelDraft(hotel, { checkIn: '2026-08-01', checkOut: '2026-08-05', adults: 2 })?.hotel
        .rooms,
    ).toBe(1)
    expect(buildHotelDraft(hotel, {})).toBeNull()
    expect(buildHotelDraft(hotel, { checkIn: '2026-08-01', checkOut: '2026-08-05' })).toBeNull()
  })

  it('checkOut yoksa checkIn + nights\'tan türetir (chat "5 gece" senaryosu)', () => {
    // Chat kriteri konaklamayı gün sayısıyla verir; checkOut boş ama seçilebilir olmalı.
    const draft = buildHotelDraft(hotel, { checkIn: '2026-08-01', nights: 4, adults: 2 })
    expect(draft).not.toBeNull()
    expect(draft?.hotel.checkIn).toBe('2026-08-01')
    expect(draft?.hotel.checkOut).toBe('2026-08-05') // 01 + 4 gece
  })

  it('checkOut varken nights\'ı yok sayar (açık tarih önceliklidir)', () => {
    const draft = buildHotelDraft(hotel, {
      checkIn: '2026-08-01',
      checkOut: '2026-08-03',
      nights: 10,
      adults: 2,
    })
    expect(draft?.hotel.checkOut).toBe('2026-08-03')
  })

  it('ne checkOut ne nights varsa null döner (booking süresi belirsiz)', () => {
    expect(buildHotelDraft(hotel, { checkIn: '2026-08-01', adults: 2 })).toBeNull()
  })
})

describe('buildFlightDraft — tripType her zaman dolu (backend @NotNull)', () => {
  it('kriterin tripType\'ını kullanır', () => {
    const draft = buildFlightDraft(flight, { tripType: 'round_trip', passengers: 2 })
    expect(draft.flight.tripType).toBe('round_trip')
    expect(draft.flight.passengerCount).toBe(2)
    // Booking, arama-satırı UUID'si (`id`) değil, TourVisio teklif jetonu (`offerId`) ile yapılmalı.
    expect(draft.offerId).toBe('offer-flt-1')
  })

  it('kriter yoksa ürünün tripType\'ına düşer', () => {
    const draft = buildFlightDraft(flight, undefined)
    expect(draft.flight.tripType).toBe('one_way')
  })

  it('kriter ve üründe yokken dönüş bacağından türetir (chat kartı senaryosu)', () => {
    const noTrip = { ...flight, tripType: undefined } as unknown as FlightProduct
    expect(buildFlightDraft(noTrip, undefined).flight.tripType).toBe('one_way')
    expect(
      buildFlightDraft({ ...noTrip, returnDepartTime: '2026-08-05T10:00:00Z' }, undefined).flight
        .tripType,
    ).toBe('round_trip')
  })
})
