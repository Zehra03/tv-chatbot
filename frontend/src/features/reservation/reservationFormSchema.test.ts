import { describe, expect, it } from 'vitest'
import {
  buildContactPhone,
  CHILD_MAX_AGE,
  CHILD_MIN_AGE,
  formatE164,
  HOTEL_CHILD_MIN_AGE,
  initialPhoneCountry,
  isValidTcKimlikNo,
  makeReservationFormSchema,
  toPreviewCommand,
  type ReservationFormValues,
} from '@/features/reservation/reservationFormSchema'
import { findCountry, parseNationalNumber } from '@/lib/countries'
import type {
  FlightReservationDraft,
  ReservationDraft,
} from '@/features/reservation/reservationDraftSlice'

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

/** Gidiş-dönüş uçuş taslağı — iki bacak, iki TourVisio jetonu. */
const roundTripDraft: FlightReservationDraft = {
  productType: 'flight',
  offerId: 'out-token',
  returnOfferId: 'ret-token',
  title: 'THY IST → AYT',
  summary: 'özet',
  price: 4200,
  currency: 'TRY',
  childAges: [],
  flight: {
    origin: 'IST',
    destination: 'AYT',
    airline: 'THY',
    tripType: 'round_trip',
    departTime: '2026-08-01T08:00:00+03:00',
    returnDepartTime: '2026-08-08T18:00:00+03:00',
    passengerCount: 1,
    price: 4200,
    currency: 'TRY',
  },
}

/** Tek yön: dönüş jetonu yok. */
const oneWayDraft: FlightReservationDraft = {
  ...roundTripDraft,
  returnOfferId: undefined,
  flight: { ...roundTripDraft.flight, tripType: 'one_way', returnDepartTime: null },
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

  /**
   * Mesaj İLK denemede gerçek aralığı söylemeli: eskiden alan önce '(0–120)' diyor, 10 yazınca
   * '18+ olmalı'ya dönüyordu — kullanıcı iki tur harcıyordu.
   */
  it('yetişkin 18+ olmalı ve hata doğrudan 18–120 aralığını gösterir', () => {
    expect(withPassenger({ age: '25' }).success).toBe(true)
    expect(withPassenger({ age: '' }).success).toBe(true) // yaş opsiyonel
    expect(firstError(withPassenger({ age: '10' }))).toBe('Geçerli bir yaş girin (18–120)')
    expect(firstError(withPassenger({ age: '200' }))).toBe('Geçerli bir yaş girin (18–120)')
    expect(firstError(withPassenger({ age: 'abc' }))).toBe('Geçerli bir yaş girin (18–120)')
  })

  it('otelde çocuk 0–17 arası ve yaşı zorunlu (otel-yalnız rezervasyonda INFANT yok)', () => {
    const child = { passengerType: 'child' as const, age: '8' }
    expect(withPassenger(child).success).toBe(true)
    expect(firstError(withPassenger({ ...child, age: '' }))).toBe('Çocuk için yaş gerekli')
    // parse() hotelSchema kullanır: uçuşsuz rezervasyonda 0-2 yaş CHILD'dır (backend
    // PreviewReservationCommand.ageMatchesType), INFANT değil — artık kabul edilir.
    expect(withPassenger({ ...child, age: '2' }).success).toBe(true)
    expect(withPassenger({ ...child, age: '0' }).success).toBe(true)
    expect(firstError(withPassenger({ ...child, age: '30' }))).toContain('0–17')
  })

  it('uçuşta çocuk hâlâ 3–17: under-3 orada INFANT olmalı (adult/child şeması infant sunmuyor)', () => {
    const flightSchema = makeReservationFormSchema('flight')
    // Ocak 1'i doğum günü seçmek, testin çalıştığı günden bağımsız olarak yaşı tam ${age} yapar
    // (yılın en erken günü olduğu için ageFromBirthDate hiçbir zaman bir yıl düşürmez).
    const birthDateForAge = (age: number) => `${new Date().getUTCFullYear() - age}-01-01`
    const flightChild = (age: number) =>
      flightSchema.safeParse({
        ...validValues,
        passengers: [{
          ...validValues.passengers[0],
          passengerType: 'child',
          age: String(age),
          birthDate: birthDateForAge(age),
          identityNumber: '10000000146',
        }],
      })
    expect(flightChild(8).success).toBe(true)
    expect(flightChild(2).success).toBe(false)
    expect(flightChild(0).success).toBe(false)
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

/**
 * Aramadaki çocuk yaş seçicisi ile şemanın kabul ettiği aralık AYNI kaynaktan gelmeli.
 * Ayrıştıklarında (arama 0–17 sunarken şema 3–17 istiyordu) kullanıcı, rezervasyon formunu
 * asla gönderemeyeceği bir yaş seçebiliyordu — yolcu satırı teklifin pax'ına sabit olduğu
 * için silinemiyor, yani akış tamamen çıkışsız kalıyordu.
 *
 * Otel-yalnız (uçuşsuz) rezervasyonda gerçek alt sınır HOTEL_CHILD_MIN_AGE (0) — backend'in
 * PassengerType.CHILD bandı (3–17) uçuş içeren rezervasyonlar için geçerli kalır (orada under-3
 * hâlâ INFANT'a ait), bkz. PreviewReservationCommand.ageMatchesType.
 */
describe('çocuk yaş aralığı — arama ve şema tek kaynak', () => {
  it('sınırlar backend PassengerType ile aynı (CHILD 3–17, otelde alt sınır 0)', () => {
    expect(CHILD_MIN_AGE).toBe(3)
    expect(CHILD_MAX_AGE).toBe(17)
    expect(HOTEL_CHILD_MIN_AGE).toBe(0)
  })

  it('otel aramasının sunduğu her yaşı (0–17) otel şeması kabul eder — seçilebilen yaş rezerve EDİLEBİLİR olmalı', () => {
    for (let age = HOTEL_CHILD_MIN_AGE; age <= CHILD_MAX_AGE; age++) {
      const result = parse({
        passengers: [{ ...validValues.passengers[0], passengerType: 'child', age: String(age) }],
      })
      expect(result.success, `${age} yaş reddedildi`).toBe(true)
    }
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

  // Yukarıdaki mutlu yol vakalarında odds*7 > evens olduğundan `%`'in işaret davranışı hiç
  // görünmüyordu. Bu vakalarda odds*7 < evens: düz `(odds*7 - evens) % 10` NEGATİF döner ve
  // 0–9 arasındaki kontrol hanesiyle asla eşleşmez → geçerli kimlikler reddedilirdi.
  it('odds*7 < evens olan GEÇERLİ kimlikleri reddetmez (negatif modulo regresyonu)', () => {
    expect(isValidTcKimlikNo('19090909018')).toBe(true) // odds*7=7, evens=36 → -29
    expect(isValidTcKimlikNo('13050914036')).toBe(true) // odds*7=14, evens=21 → -7
    expect(isValidTcKimlikNo('18070505028')).toBe(true) // odds*7=7,  evens=25 → -18
  })

  it('negatif modulo dalında da bozuk kontrol hanesini yakalar', () => {
    expect(isValidTcKimlikNo('19090909019')).toBe(false) // son hane bozuk
    expect(isValidTcKimlikNo('19090909028')).toBe(false) // 10. hane bozuk
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

  /**
   * PARA-KRİTİK: TourVisio gidiş-dönüşü iki ayrı jetonla satar. Yalnız gidiş jetonunu
   * göndermek, kullanıcı gidiş-dönüş ücretini ödemişken TEK YÖN bilet alır. Bu dal tamamen
   * kapsamsızdı: ternary sadeleştirilse ya da buildFlightDraft `returnOfferId`'i taşımayı
   * bıraksa tüm suite yeşil kalıyordu.
   */
  it('gidiş-dönüşte İKİ teklif jetonu gönderir', () => {
    const cmd = toPreviewCommand(roundTripDraft, validValues)
    expect(cmd.offerIds).toEqual(['out-token', 'ret-token'])
  })

  it('tek yönde yalnız gidiş jetonunu gönderir', () => {
    expect(toPreviewCommand(oneWayDraft, validValues).offerIds).toEqual(['out-token'])
  })

  it('returnOfferId null ise dönüş jetonu eklenmez (tek yön gibi davranır)', () => {
    const draft = { ...roundTripDraft, returnOfferId: null }
    expect(toPreviewCommand(draft, validValues).offerIds).toEqual(['out-token'])
  })
})
