import type { HotelLocation, HotelProduct } from '@/types'

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
    // Placeholder görsel (picsum) — gerçek otel fotoğrafı DEĞİL, yalnızca kart düzenini doldurur.
    image: 'https://picsum.photos/seed/htl-mock-001/400/300',
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
    image: 'https://picsum.photos/seed/htl-mock-002/400/300',
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
    image: 'https://picsum.photos/seed/htl-mock-003/400/300',
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
    // Görselsiz — kartın placeholder davranışını gösterir (TourVisio'da görseli olmayan otel gibi).
    image: null,
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
    image: 'https://picsum.photos/seed/htl-mock-005/400/300',
  },
]

/**
 * ⚠️ MOCK destination önerileri — otel "Nereye" otomatik tamamlaması için. Gerçek
 * şehir adları ama liste dev-mock'tur (backend `HotelSearchServiceImpl.suggestLocations`
 * paritesi). GET /api/v1/hotels/locations handler'ı bunları isim ile filtreler.
 */
export const hotelLocationFixtures: HotelLocation[] = [
  { id: '23494', name: 'Antalya', type: 'city' },
  { id: '100', name: 'Antakya', type: 'city' },
  { id: '200', name: 'Bodrum', type: 'city' },
  { id: '300', name: 'İstanbul', type: 'city' },
  { id: '400', name: 'İzmir', type: 'city' },
  { id: '500', name: 'Nevşehir', type: 'city' },
  { id: '600', name: 'Muğla', type: 'city' },
  { id: '700', name: 'Bursa', type: 'city' },
]
