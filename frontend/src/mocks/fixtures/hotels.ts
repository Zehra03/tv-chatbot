import type { HotelProduct } from '@/types'

/**
 * ⚠️ MOCK VERİ — TAMAMEN UYDURMA. Gerçek TourVisio otel/fiyat/uygunluk değildir.
 * İsimler bilerek "MOCK/Test" içerir ve fiyatlar yuvarlak placeholder'dır ki
 * gerçek gibi görünmesin (CLAUDE.md: "gerçek gibi görünen sahte fiyat üretme").
 * Yalnızca development'ta MSW üzerinden servis edilir.
 */
export const hotelFixtures: HotelProduct[] = [
  {
    id: 'htl-mock-001',
    hotelName: 'MOCK Grand Antalya Resort',
    region: 'Antalya',
    stars: 5,
    price: 1200,
    currency: 'EUR',
    boardType: 'AI',
    availability: true,
  },
  {
    id: 'htl-mock-002',
    hotelName: 'Test Seaside Hotel Bodrum',
    region: 'Bodrum',
    stars: 4,
    price: 800,
    currency: 'EUR',
    boardType: 'HB',
    availability: true,
  },
  {
    id: 'htl-mock-003',
    hotelName: 'MOCK City Suites İstanbul',
    region: 'İstanbul',
    stars: 4,
    price: 600,
    currency: 'EUR',
    boardType: 'BB',
    availability: true,
  },
  {
    id: 'htl-mock-004',
    hotelName: 'Sample Boutique Kapadokya',
    region: 'Nevşehir',
    stars: 3,
    price: 350,
    currency: 'EUR',
    boardType: 'RO',
    availability: false,
  },
  {
    id: 'htl-mock-005',
    hotelName: 'MOCK Palace İzmir',
    region: 'İzmir',
    stars: 5,
    price: 1500,
    currency: 'EUR',
    boardType: 'UAI',
    availability: true,
  },
]
