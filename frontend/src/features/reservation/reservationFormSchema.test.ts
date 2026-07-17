import { describe, expect, it } from 'vitest'
import {
  buildContactPhone,
  formatE164,
  initialPhoneCountry,
  isValidTcKimlikNo,
  makeReservationFormSchema,
  toPreviewCommand,
  type ReservationFormValues,
} from '@/features/reservation/reservationFormSchema'
import { findCountry, parseNationalNumber } from '@/lib/countries'
import type { ReservationDraft } from '@/features/reservation/reservationDraftSlice'

/**
 * Telefon/uyruk eşleştirmesinin ve alan kurallarının birim testleri. Bu yardımcılar önceden
 * yalnız sayfa testlerinden dolaylı geçiyordu ve hepsi TR mutlu yolunu kullandığı için eski
 * splitPhone'un "her numara +90'dır" varsayımı hiç sınanmamıştı.
 */

const hotelDraft: ReservationDraft = {
  productType: 'hotel',
  offerId: 'off-1',
  title: 'Otel',
  summary: 'özet',
  price: 1200,
  currency: 'EUR',
  childAges: [],
  hotel: {
    hotelName: 'Otel',
    region: 'Antalya',
    stars: 5,
    boardType: 'AI',
    checkIn: '2026-08-01',
    checkOut: '2026-08-05',
    rooms: 1,
    adults: 1,
    children: 0,
    nationality: 'TR',
    price: 1200,
    currency: 'EUR',
  },
}

const validValues: ReservationFormValues = {
  passengers: [
    {
      firstName: 'Zehra',
      lastName: 'Yılmaz',
      passengerType: 'adult',
      title: '1',
      age: '',
      nationality: 'TR',
      birthDate: '',
      identityNumber: '',
    },
  ],
  email: 'zehra@example.com',
  phoneCountry: 'TR',
  phoneNumber: '5551112233',
}

const hotelSchema = makeReservationFormSchema('hotel')
const parse = (overrides: Partial<ReservationFormValues>) =>
  hotelSchema.safeParse({ ...validValues, ...overrides })

/** İlk hatanın mesajı — hangi alanda patladığını okunur biçimde döndürür. */
const firstError = (result: ReturnType<typeof parse>) =>
  result.success ? null : result.error.issues[0].message

describe('ülke ↔ telefon eşleştirmesi', () => {
  it('ülke kodunu seçilen ülkeden alır — tahmin etmez', () => {
    expect(buildContactPhone('TR', '5551112233')).toEqual({
      countryCode: '90',
      areaCode: '555',
      phoneNumber: '1112233',
    })
    // Eski splitPhone bunu da '90' sayıyordu; Alman numarası Türk numarası olarak gidiyordu.
    expect(buildContactPhone('DE', '15112345678').countryCode).toBe('49')
    expect(formatE164('DE', '15112345678')).toBe('+4915112345678')
  })

  it('aynı arama kodunu paylaşan ülkeler ayrı ayrı saklanır', () => {
    // Değer ISO kodudur; +1'i ABD ve Kanada, +7'yi Rusya ve Kazakistan paylaşır.
    expect(findCountry('US')?.dial).toBe('1')
    expect(findCountry('CA')?.dial).toBe('1')
    expect(buildContactPhone('KZ', '7012345678').countryCode).toBe('7')
  })

  it('telefon ülkesi ana misafirin uyruğundan açılır', () => {
    expect(initialPhoneCountry(hotelDraft)).toBe('TR')
    expect(
      initialPhoneCountry({ ...hotelDraft, hotel: { ...hotelDraft.hotel, nationality: 'DE' } }),
    ).toBe('DE')
    // Uyruk yoksa/tanınmazsa varsayılana düşer.
    expect(
      initialPhoneCountry({ ...hotelDraft, hotel: { ...hotelDraft.hotel, nationality: 'ZZ' } }),
    ).toBe('TR')
  })
})

describe('parseNationalNumber', () => {
  it('yapıştırılan ülke kodunu ve trunk 0’ı düşürür', () => {
    expect(parseNationalNumber('+90 (555) 111 22 33', 'TR')).toBe('5551112233')
    expect(parseNationalNumber('0555 111 22 33', 'TR')).toBe('5551112233')
    expect(parseNationalNumber('5551112233', 'TR')).toBe('5551112233')
  })

  it('numara zaten NSN’ye oturuyorsa arama koduyla başlasa da kırpmaz', () => {
    // Rusya'da arama kodu '7' ve ulusal numara da 7 ile başlayabilir — baştaki haneyi yememeli.
    expect(parseNationalNumber('7123456789', 'RU')).toBe('7123456789')
  })

  it('ülkenin en fazla hane sayısına kırpar', () => {
    expect(parseNationalNumber('55511122334444', 'TR')).toHaveLength(10)
  })

  it('İtalya’da baştaki 0 korunur (numaranın parçası)', () => {
    expect(parseNationalNumber('06 6982 0000', 'IT')).toBe('0669820000')
  })
})

describe('telefon validasyonu', () => {
  it('ülkenin hane kuralını uygular', () => {
    expect(parse({ phoneNumber: '555' }).success).toBe(false)
    expect(firstError(parse({ phoneNumber: '555' }))).toContain('10 haneli')
    expect(parse({ phoneNumber: '5551112233' }).success).toBe(true)
    // Almanya'da aralık geniş: aynı numara oradaki kurala da uyar.
    expect(parse({ phoneCountry: 'DE', phoneNumber: '15112345678' }).success).toBe(true)
  })

  it('doğru uzunlukta ama imkânsız başlangıcı reddeder', () => {
    // '9055511122' 10 hanedir ama Türkiye numarası 9 ile başlamaz (ülke kodu numaraya yazılmış).
    expect(firstError(parse({ phoneNumber: '9055511122' }))).toContain('2 ile 5 arasında')
  })

  it('tanınmayan ülke kodunu reddeder', () => {
    expect(parse({ phoneCountry: 'ZZ' }).success).toBe(false)
    expect(parse({ phoneCountry: '' }).success).toBe(false)
  })
})

describe('uyruk validasyonu', () => {
  it('yalnız gerçek ISO ülkelerini kabul eder', () => {
    expect(parse({ passengers: [{ ...validValues.passengers[0], nationality: 'DE' }] }).success).toBe(true)
    // Eski regex iki harfli her diziyi geçiriyordu.
    expect(parse({ passengers: [{ ...validValues.passengers[0], nationality: 'ZZ' }] }).success).toBe(false)
    expect(parse({ passengers: [{ ...validValues.passengers[0], nationality: '' }] }).success).toBe(false)
  })
})

describe('ad/soyad validasyonu', () => {
  it('Türkçe harfleri kabul eder, rakam ve simgeleri reddeder', () => {
    expect(parse({ passengers: [{ ...validValues.passengers[0], firstName: 'Şeyma Nur' }] }).success).toBe(true)
    expect(parse({ passengers: [{ ...validValues.passengers[0], lastName: "O'Brien" }] }).success).toBe(true)
    expect(parse({ passengers: [{ ...validValues.passengers[0], firstName: 'Zehra42' }] }).success).toBe(false)
    expect(parse({ passengers: [{ ...validValues.passengers[0], firstName: 'Z' }] }).success).toBe(false)
  })
})

describe('yaş ↔ yolcu tipi tutarlılığı (backend kuralının kopyası)', () => {
  const withPassenger = (extra: Partial<ReservationFormValues['passengers'][number]>) =>
    parse({ passengers: [{ ...validValues.passengers[0], ...extra }] })

  it('yetişkin 18+ olmalı', () => {
    expect(withPassenger({ age: '25' }).success).toBe(true)
    expect(withPassenger({ age: '' }).success).toBe(true) // yaş opsiyonel
    expect(firstError(withPassenger({ age: '5' }))).toContain('en az 18')
  })

  it('çocuk 3–17 arası ve yaşı zorunlu', () => {
    const child = { passengerType: 'child' as const, age: '8' }
    expect(withPassenger(child).success).toBe(true)
    expect(firstError(withPassenger({ ...child, age: '' }))).toBe('Çocuk için yaş gerekli')
    // 2 yaş bebektir (backend: INFANT 0-2) — 'çocuk' olarak gönderilemez.
    expect(firstError(withPassenger({ ...child, age: '2' }))).toContain('3–17')
    expect(firstError(withPassenger({ ...child, age: '30' }))).toContain('3–17')
  })
})

describe('uçuş alanları (doğum tarihi + kimlik no)', () => {
  const flightSchema = makeReservationFormSchema('flight')
  const flightValues = (extra: Partial<ReservationFormValues['passengers'][number]>) => ({
    ...validValues,
    passengers: [
      {
        ...validValues.passengers[0],
        birthDate: '1995-04-12',
        identityNumber: '10000000146',
        ...extra,
      },
    ],
  })

  it('uçuşta ikisi de zorunlu, otelde değil', () => {
    expect(flightSchema.safeParse(flightValues({})).success).toBe(true)
    expect(flightSchema.safeParse(flightValues({ birthDate: '' })).success).toBe(false)
    expect(flightSchema.safeParse(flightValues({ identityNumber: '' })).success).toBe(false)
    // Otelde boş bırakılabilir.
    expect(hotelSchema.safeParse(validValues).success).toBe(true)
  })

  it('takvimde olmayan ve gelecekteki tarihleri eler', () => {
    expect(flightSchema.safeParse(flightValues({ birthDate: '2025-02-31' })).success).toBe(false)
    expect(flightSchema.safeParse(flightValues({ birthDate: '2999-01-01' })).success).toBe(false)
  })

  it('doğum tarihi girilen yaşla çelişemez', () => {
    const result = flightSchema.safeParse(flightValues({ birthDate: '1995-04-12', age: '60' }))
    expect(result.success).toBe(false)
    expect(result.success ? null : result.error.issues[0].message).toContain('uyuşmuyor')
  })

  it('TR uyruklu yolcuda TC kimlik no algoritması uygulanır', () => {
    // Doğru sağlama ile geçer, tek hane değişince (algoritma bozulur) reddedilir.
    expect(flightSchema.safeParse(flightValues({})).success).toBe(true)
    expect(flightSchema.safeParse(flightValues({ identityNumber: '10000000145' })).success).toBe(false)
    expect(flightSchema.safeParse(flightValues({ identityNumber: '12345678901' })).success).toBe(false)
    // Yabancı yolcunun TCKN'si yok: 11 hane yeterli, sağlama aranmaz.
    expect(
      flightSchema.safeParse(flightValues({ nationality: 'DE', identityNumber: '12345678901' }))
        .success,
    ).toBe(true)
  })
})

describe('isValidTcKimlikNo', () => {
  it('algoritmayı doğrular', () => {
    expect(isValidTcKimlikNo('10000000146')).toBe(true)
    expect(isValidTcKimlikNo('10000000145')).toBe(false) // son hane bozuk
    expect(isValidTcKimlikNo('00000000146')).toBe(false) // 0 ile başlayamaz
    expect(isValidTcKimlikNo('1234567890')).toBe(false) // 10 hane
    expect(isValidTcKimlikNo('abcdefghijk')).toBe(false)
  })
})

describe('toPreviewCommand', () => {
  it('lider yolcuya yapısal + düz telefonu birlikte yazar', () => {
    const cmd = toPreviewCommand(hotelDraft, validValues)
    const lead = cmd.travellers[0]
    expect(lead.leader).toBe(true)
    expect(lead.contactPhone).toEqual({ countryCode: '90', areaCode: '555', phoneNumber: '1112233' })
    expect(lead.phone).toBe('+905551112233')
    expect(lead.nationalityCode).toBe('TR')
    expect(lead.address?.email).toBe('zehra@example.com')
  })
})
