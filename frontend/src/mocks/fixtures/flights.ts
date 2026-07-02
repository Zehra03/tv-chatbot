import type { FlightProduct } from '@/types'

/**
 * ⚠️ MOCK VERİ — TAMAMEN UYDURMA. Gerçek uçuş/fiyat/uygunluk değildir.
 * Havayolu isimleri bilerek "Mock/Test"tir; saatler ve fiyatlar placeholder.
 * (CLAUDE.md: fiyat/uygunluk asla uydurulmaz — bu veri yalnızca dev-mock'tur.)
 */
export const flightFixtures: FlightProduct[] = [
  {
    id: 'flt-mock-001',
    airline: 'MockAir',
    origin: 'İstanbul',
    destination: 'Antalya',
    departTime: '2026-08-01T08:00:00Z',
    arriveTime: '2026-08-01T09:30:00Z',
    tripType: 'one_way',
    returnDepartTime: null,
    returnArriveTime: null,
    stops: 0,
    baggage: '20kg',
    price: 120,
    currency: 'EUR',
  },
  {
    id: 'flt-mock-002',
    airline: 'TestJet',
    origin: 'İstanbul',
    destination: 'Antalya',
    departTime: '2026-08-01T14:00:00Z',
    arriveTime: '2026-08-01T16:45:00Z',
    tripType: 'one_way',
    returnDepartTime: null,
    returnArriveTime: null,
    stops: 1,
    baggage: '15kg',
    price: 90,
    currency: 'EUR',
  },
  {
    id: 'flt-mock-003',
    airline: 'MockAir',
    origin: 'İstanbul',
    destination: 'Antalya',
    departTime: '2026-08-01T07:00:00Z',
    arriveTime: '2026-08-01T08:30:00Z',
    tripType: 'round_trip',
    returnDepartTime: '2026-08-08T18:00:00Z',
    returnArriveTime: '2026-08-08T19:30:00Z',
    stops: 0,
    baggage: '20kg',
    price: 220,
    currency: 'EUR',
  },
  {
    id: 'flt-mock-004',
    airline: 'SampleWings',
    origin: 'Ankara',
    destination: 'İzmir',
    departTime: '2026-09-10T11:00:00Z',
    arriveTime: '2026-09-10T12:10:00Z',
    tripType: 'one_way',
    returnDepartTime: null,
    returnArriveTime: null,
    stops: 0,
    baggage: '20kg',
    price: 100,
    currency: 'EUR',
  },
]
