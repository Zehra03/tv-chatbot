import type { FlightLocation, FlightProduct } from '@/types'

/**
 * ⚠️ MOCK VERİ — TAMAMEN UYDURMA. Gerçek uçuş/fiyat/uygunluk değildir.
 * Havayolu isimleri bilerek "Mock/Test"tir; saatler ve fiyatlar placeholder.
 * (CLAUDE.md: fiyat/uygunluk asla uydurulmaz — bu veri yalnızca dev-mock'tur.)
 */
export const flightFixtures: FlightProduct[] = [
  {
    id: 'flt-mock-001',
    offerId: 'offer-mock-001',
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
    offerId: 'offer-mock-002',
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
    offerId: 'offer-mock-003',
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
    offerId: 'offer-mock-004',
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

/**
 * ⚠️ MOCK konum önerileri — kalkış/varış otomatik tamamlama için. Gerçek IATA
 * kodları taşır ama liste dev-mock'tur (backend `MockFlightLocationService` ile
 * paralel). GET /api/v1/flights/locations handler'ı bunları isim/id ile filtreler.
 */
export const flightLocationFixtures: FlightLocation[] = [
  { id: 'IST', code: 'IST', name: 'İstanbul Havalimanı (IST)', type: 'airport' },
  { id: 'SAW', code: 'SAW', name: 'İstanbul Sabiha Gökçen (SAW)', type: 'airport' },
  { id: 'AYT', code: 'AYT', name: 'Antalya Havalimanı (AYT)', type: 'airport' },
  { id: 'ESB', code: 'ESB', name: 'Ankara Esenboğa (ESB)', type: 'airport' },
  { id: 'ADB', code: 'ADB', name: 'İzmir Adnan Menderes (ADB)', type: 'airport' },
  { id: 'DLM', code: 'DLM', name: 'Dalaman (DLM)', type: 'airport' },
  { id: 'BJV', code: 'BJV', name: 'Bodrum Milas (BJV)', type: 'airport' },
  { id: 'LHR', code: 'LHR', name: 'London Heathrow (LHR)', type: 'airport' },
  { id: 'CDG', code: 'CDG', name: 'Paris Charles de Gaulle (CDG)', type: 'airport' },
  { id: 'FRA', code: 'FRA', name: 'Frankfurt (FRA)', type: 'airport' },
]
