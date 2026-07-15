import type { ReservationDetail } from '@/types'

/**
 * ⚠️ MOCK VERİ — TAMAMEN UYDURMA rezervasyonlar (dev-mock). PII değildir;
 * isim/iletişim tamamen sahtedir. Handler bu diziyi klonlayıp üzerinde mutasyon
 * yapar (oluştur/iptal), böylece liste çağrılar arası tutarlı davranır. Yeni backend
 * sözleşmesiyle (ReservationDetailResponse) hizalı: id number, otel/uçuş bloğu +
 * iptal seçenekleri; liste handler'ı bunlardan yalnız özet alanları döndürür.
 */
export const reservationFixtures: ReservationDetail[] = [
  {
    id: 1001,
    reservationNumber: 'PAX-MOCK-1001',
    externalReservationNumber: 'RC001001',
    productType: 'hotel',
    status: 'confirmed',
    reservationDate: '2026-06-20',
    totalAmount: 1200,
    currency: 'EUR',
    leadGuestName: 'Ayşe Mock',
    passengers: [
      {
        firstName: 'Ayşe',
        lastName: 'Mock',
        passengerType: 'adult',
        age: 34,
        nationality: 'TR',
        email: 'ayse.mock@example.com',
        phone: '+900000000001',
      },
    ],
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
    cancellationOptions: [
      {
        reasonId: 'RSN-USER',
        reasonName: 'Kullanıcı talebi',
        cancelable: true,
        price: { amount: 0, currency: 'EUR' },
        services: [],
      },
    ],
  },
  {
    id: 1002,
    reservationNumber: 'PAX-MOCK-1002',
    externalReservationNumber: 'RC001002',
    productType: 'flight',
    status: 'pending',
    reservationDate: '2026-06-25',
    totalAmount: 220,
    currency: 'EUR',
    leadGuestName: 'Test Yolcu',
    passengers: [
      {
        firstName: 'Test',
        lastName: 'Yolcu',
        passengerType: 'adult',
        age: 28,
        nationality: 'TR',
        email: 'test.yolcu@example.com',
        phone: '+900000000002',
      },
    ],
    flight: {
      origin: 'IST',
      destination: 'AYT',
      airline: 'MOCK Air',
      tripType: 'one_way',
      departTime: '2026-07-05T08:30:00+03:00',
      arriveTime: '2026-07-05T09:45:00+03:00',
      returnDepartTime: null,
      returnArriveTime: null,
      stops: 0,
      baggage: '20kg',
      passengerCount: 1,
      price: 220,
      currency: 'EUR',
    },
    cancellationOptions: [
      {
        reasonId: 'RSN-USER',
        reasonName: 'Kullanıcı talebi',
        cancelable: true,
        price: { amount: 25, currency: 'EUR' },
        services: [],
      },
    ],
  },
  {
    id: 1003,
    reservationNumber: 'PAX-MOCK-1003',
    externalReservationNumber: 'RC001003',
    productType: 'hotel',
    status: 'cancelled',
    reservationDate: '2026-05-30',
    totalAmount: 800,
    currency: 'EUR',
    leadGuestName: 'Sample Guest',
    passengers: [
      {
        firstName: 'Sample',
        lastName: 'Guest',
        passengerType: 'adult',
        age: 45,
        nationality: 'DE',
        email: 'sample.guest@example.com',
        phone: '+490000000003',
      },
      {
        firstName: 'Mini',
        lastName: 'Guest',
        passengerType: 'child',
        age: 8,
        nationality: 'DE',
      },
    ],
    hotel: {
      hotelName: 'Test Seaside Hotel Bodrum',
      region: 'Bodrum',
      stars: 4,
      boardType: 'HB',
      checkIn: '2026-06-05',
      checkOut: '2026-06-10',
      rooms: 1,
      adults: 2,
      children: 1,
      nationality: 'DE',
      price: 800,
      currency: 'EUR',
    },
    // İptal edilmiş → iptal seçeneği yok.
    cancellationOptions: [],
  },
]
