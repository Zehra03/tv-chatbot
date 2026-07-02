import type { Reservation } from '@/types'

/**
 * ⚠️ MOCK VERİ — TAMAMEN UYDURMA rezervasyonlar (dev-mock). PII değildir;
 * isim/iletişim tamamen sahtedir. Handler bu diziyi klonlayıp üzerinde mutasyon
 * yapar (oluştur/iptal), böylece liste çağrılar arası tutarlı davranır.
 */
export const reservationFixtures: Reservation[] = [
  {
    id: '1001',
    reservationNumber: 'PAX-MOCK-1001',
    productType: 'hotel',
    status: 'confirmed',
    reservationDate: '2026-06-20',
    totalAmount: 1200,
    currency: 'EUR',
    leadGuestName: 'Ayşe Mock',
    createdAt: '2026-06-20T10:15:00Z',
    updatedAt: '2026-06-20T10:15:00Z',
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
  },
  {
    id: '1002',
    reservationNumber: 'PAX-MOCK-1002',
    productType: 'flight',
    status: 'pending',
    reservationDate: '2026-06-25',
    totalAmount: 220,
    currency: 'EUR',
    leadGuestName: 'Test Yolcu',
    createdAt: '2026-06-25T09:00:00Z',
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
  },
  {
    id: '1003',
    reservationNumber: 'PAX-MOCK-1003',
    productType: 'hotel',
    status: 'cancelled',
    reservationDate: '2026-05-30',
    totalAmount: 800,
    currency: 'EUR',
    leadGuestName: 'Sample Guest',
    createdAt: '2026-05-30T12:00:00Z',
    updatedAt: '2026-06-01T08:00:00Z',
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
  },
]
