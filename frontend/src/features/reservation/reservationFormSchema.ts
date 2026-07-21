import { z } from 'zod'
import type { PreviewReservationCommand, TravellerInput } from '@/api'
import type { ReservationDraft } from '@/features/reservation/reservationDraftSlice'
import {
  DEFAULT_COUNTRY_CODE,
  findCountry,
  isCountryCode,
  normalizeNationalNumber,
  nsnRange,
} from '@/lib/countries'

/**
 * Rezervasyon formu sınır validasyonu (docs/frontend-architecture.md §9,
 * CLAUDE.md: formlar Zod ile valide edilir). TourVisio her yolcu için ünvan (Bay/Bayan) ve uyruk
 * ZORUNLU ister; ayrıca yolcu SAYISI teklifin pax'ıyla (yetişkin+çocuk) eşleşmelidir — bu yüzden
 * satırlar aramadan önden doldurulur ve tip/ sayı sabittir. Sayısal alanlar formda string taşınır.
 *
 * Kurallar backend Bean Validation'ını (PreviewReservationCommand) birebir yansıtır: yaş↔tip
 * tutarlılığı, uçuşta doğum tarihi + kimlik no. Sunucuda da kontrol edilir; buradaki kopya hatayı
 * ALANIN yanında, istek gitmeden gösterir.
 */

/** TourVisio ünvan kodu: 1 = Bay, 2 = Bayan. */
export const TITLE_OPTIONS = [
  { value: '1', label: 'Bay' },
  { value: '2', label: 'Bayan' },
] as const

/**
 * Backend `PassengerType` bantlarının (ADULT 18+, CHILD 3–17, INFANT 0–2) istemci kopyası.
 * Ağdan gelmediği için gerçek anlamda "tek kaynak" DEĞİL — elle senkron tutulan bir kopya; bu
 * yüzden `passengerAgeBands.contract.test.ts` backend enum'ını okuyup değerleri karşılaştırır ve
 * ayrışırlarsa CI'yı kırar (aksi hâlde form, sunucunun reddedeceği bir yaşı kabul etmeye başlar).
 */
export const ADULT_MIN_AGE = 18
const INFANT_MAX_AGE = 2
export const MAX_AGE = 120

/**
 * Çocuk yolcunun kabul edilen yaş aralığı — ARAMA formu da bunu kullanır (HotelsPage).
 * Tek kaynak olması şart: aralık iki yerde ayrı ayrı yazılıydı ve ayrışmıştı. Arama 0–17
 * sunarken şema 3–17 dayatıyordu, arada kalan 0–2 yaş "bebek" seçimi rezervasyon formunu
 * GÖNDERİLEMEZ hâle getiriyordu (satır teklifin pax'ına sabit olduğu için silinemiyor da).
 *
 * 0–2 (INFANT) otelde hiç geçerli değil: backend PassengerType'a göre INFANT bir havayolu
 * ücret tipidir (kucakta bebek) ve uçuşu olmayan bir rezervasyon INFANT taşıyamaz.
 */
export const CHILD_MIN_AGE = INFANT_MAX_AGE + 1
export const CHILD_MAX_AGE = ADULT_MIN_AGE - 1

/** DB sütun sınırları (passengers.first_name/last_name varchar(100), email varchar(254)). */
const NAME_MAX = 50
const EMAIL_MAX = 254

/**
 * Ad/soyad: harfle başlar; harf, boşluk, kesme ve tire içerebilir. \p{L}+\p{M} Türkçe ve diğer
 * alfabelerdeki harfleri/aksanları kapsar — [A-Za-z] "Şeyma"yı reddederdi.
 */
const NAME_RE = /^\p{L}[\p{L}\p{M}\s'’.-]*$/u
const NAME_MESSAGE = 'Yalnızca harf, boşluk ve - kısaltma işaretleri kullanın'

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/

/** yyyy-MM-dd gerçek bir takvim günü mü? ('2025-02-31' Date ile 3 Mart'a kayar — onu da eler.) */
function isRealDate(value: string): boolean {
  if (!ISO_DATE_RE.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

/** Doğum tarihinden bugünkü yaş (tam yıl). */
export function ageFromBirthDate(value: string, today = new Date()): number {
  const birth = new Date(`${value}T00:00:00Z`)
  let age = today.getUTCFullYear() - birth.getUTCFullYear()
  const monthDelta = today.getUTCMonth() - birth.getUTCMonth()
  if (monthDelta < 0 || (monthDelta === 0 && today.getUTCDate() < birth.getUTCDate())) age -= 1
  return age
}

/**
 * T.C. kimlik numarası algoritması: 11 hane, ilk hane 0 olamaz, 10. hane
 * ((tekler×7) − çiftler) mod 10, 11. hane ilk 10 hanenin toplamının mod 10'u.
 * Yalnız TR uyruklu yolcuya uygulanır — yabancı yolcunun TCKN'si yoktur.
 *
 * `(x % 10 + 10) % 10`: kural MATEMATİKSEL modulo ister, JS'in `%` operatörü ise kalanın
 * işaretini korur. odds*7 < evens olduğunda (ör. 19090909018 → 7 − 36 = −29) düz `% 10`
 * negatif döner ve 0–9 aralığındaki kontrol hanesiyle ASLA eşleşemez; gerçek TCKN'lerin
 * ~binde 0,1'i bu yüzden sessizce reddediliyordu (uçuş rezervasyonu tümden bloke).
 */
export function isValidTcKimlikNo(value: string): boolean {
  if (!/^\d{11}$/.test(value) || value[0] === '0') return false
  const d = value.split('').map(Number)
  const odds = d[0] + d[2] + d[4] + d[6] + d[8]
  const evens = d[1] + d[3] + d[5] + d[7]
  const tenth = ((odds * 7 - evens) % 10 + 10) % 10
  if (tenth !== d[9]) return false
  const sumOfFirstTen = d.slice(0, 10).reduce((total, digit) => total + digit, 0)
  return sumOfFirstTen % 10 === d[10]
}

const nameSchema = (label: string) =>
  z
    .string()
    .trim()
    .min(2, 'En az 2 karakter girin')
    .max(NAME_MAX, `${label} en fazla ${NAME_MAX} karakter olabilir`)
    .regex(NAME_RE, NAME_MESSAGE)

/** Uyruk: gerçek bir ISO-3166 ülkesi olmalı — iki harfli her dizi (ör. 'ZZ') geçerli değil. */
const nationalitySchema = z
  .string()
  .trim()
  .min(1, 'Uyruk seçin')
  .refine(isCountryCode, 'Listeden bir ülke seçin')

export const makePassengerSchema = (productType: ReservationDraft['productType']) =>
  z
    .object({
      firstName: nameSchema('Ad'),
      lastName: nameSchema('Soyad'),
      passengerType: z.enum(['adult', 'child']),
      // Ünvan zorunlu (TourVisio setReservationInfo şartı).
      title: z.enum(['1', '2']),
      /**
       * Aralık kontrolü burada DEĞİL, superRefine'da yapılır: sınır yolcu tipine bağlı
       * (yetişkin 18–120, çocuk 3–17) ve tip bu alandan görünmez. Tek aşamalı kontrol,
       * kullanıcının önce "0–120" görüp 10 yazdıktan sonra "18+ olmalı" ile karşılaşmasını
       * engeller — ilk uyarı doğrudan gerçek aralığı söyler.
       */
      age: z.string().optional(),
      // Uyruk zorunlu (TourVisio şartı); aramadan önden doldurulur.
      nationality: nationalitySchema,
      // Doğum tarihi (yyyy-MM-dd) ve TC kimlik no — uçuş biletlemesi için ZORUNLU, otelde opsiyonel.
      birthDate: z
        .string()
        .optional()
        .refine((v) => !v || isRealDate(v), 'Geçerli bir tarih girin'),
      identityNumber: z.string().optional(),
    })
    .superRefine((p, ctx) => {
      const rawAge = p.age?.trim() ?? ''
      const isChild = p.passengerType === 'child'
      // Yaş ↔ tip tutarlılığı — backend'in isTravellerAgeConsistentWithType kuralı.
      const [minAge, maxAge] = isChild ? [CHILD_MIN_AGE, CHILD_MAX_AGE] : [ADULT_MIN_AGE, MAX_AGE]
      const age = /^\d{1,3}$/.test(rawAge) ? Number(rawAge) : null

      if (!rawAge) {
        // Yetişkinde yaş opsiyonel (uçuşta doğum tarihi ayrıca zorunlu), çocukta şart.
        if (isChild) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['age'], message: 'Çocuk için yaş gerekli' })
        }
      } else if (age === null || age < minAge || age > maxAge) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['age'],
          message: `Geçerli bir yaş girin (${minAge}–${maxAge})`,
        })
      }

      if (p.birthDate && isRealDate(p.birthDate)) {
        const derived = ageFromBirthDate(p.birthDate)
        if (derived < 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['birthDate'],
            message: 'Doğum tarihi gelecekte olamaz',
          })
        } else if (derived > MAX_AGE) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['birthDate'],
            message: 'Doğum tarihini kontrol edin',
          })
          // Yaş da girilmişse ikisi çelişmemeli: TourVisio'ya çelişkili yolcu gitmesin.
        } else if (age !== null && Math.abs(derived - age) > 1) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['birthDate'],
            message: `Doğum tarihi girilen yaşla (${age}) uyuşmuyor`,
          })
        }
      }

      // Uçuş biletlemesi: her yolcu için doğum tarihi + kimlik no zorunlu (TourVisio
      // ParameterCanNotBeNull ile onaydan SONRA reddediyor — burada önden yakalanır).
      if (productType === 'flight') {
        if (!p.birthDate?.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['birthDate'],
            message: 'Uçuş için doğum tarihi zorunlu',
          })
        }
        const identity = p.identityNumber?.trim() ?? ''
        if (!identity) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['identityNumber'],
            message: 'Uçuş için kimlik no zorunlu',
          })
        } else if (!/^\d{11}$/.test(identity)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['identityNumber'],
            message: 'Kimlik no 11 haneli olmalı',
          })
        } else if (p.nationality.toUpperCase() === 'TR' && !isValidTcKimlikNo(identity)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['identityNumber'],
            message: 'TC kimlik no doğrulanamadı — lütfen kontrol edin',
          })
        }
      } else if (p.identityNumber?.trim() && !/^\d{11}$/.test(p.identityNumber.trim())) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['identityNumber'],
          message: 'Kimlik no 11 haneli olmalı',
        })
      }
    })

export const makeReservationFormSchema = (productType: ReservationDraft['productType']) =>
  z
    .object({
      passengers: z.array(makePassengerSchema(productType)).min(1, 'En az bir yolcu girin'),
      email: z
        .string()
        .trim()
        .min(1, 'E-posta girin')
        .email('Geçerli bir e-posta girin')
        .max(EMAIL_MAX, 'E-posta çok uzun'),
      /** Telefonun ÜLKESİ (ISO alpha-2) — arama kodu buradan türetilir (+1'i ABD/Kanada paylaşır). */
      phoneCountry: z.string().trim().min(1, 'Telefon ülke kodunu seçin').refine(isCountryCode, 'Geçerli bir ülke seçin'),
      /** Ulusal numara: ülke kodu ve baştaki 0 olmadan, yalnız rakam. */
      phoneNumber: z.string().trim().min(1, 'Telefon numarası girin'),
    })
    .superRefine((values, ctx) => {
      const country = findCountry(values.phoneCountry)
      if (!country) return
      // Ayırıcılar (boşluk, parantez, tire) serbest; harf ya da '+' değil — ülke kodu ayrı alanda.
      if (!/^\d+$/.test(values.phoneNumber.replace(/[\s()-]/g, ''))) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['phoneNumber'],
          message: 'Telefon numarası yalnızca rakam içerebilir',
        })
        return
      }
      const digits = normalizeNationalNumber(values.phoneNumber, country.code)
      if (!digits) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['phoneNumber'],
          message: 'Telefon numarası girin',
        })
        return
      }
      const [min, max] = nsnRange(country.code)
      if (digits.length < min || digits.length > max) {
        const expected = min === max ? `${min} haneli` : `${min}–${max} haneli`
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['phoneNumber'],
          message: `${country.name} için telefon numarası ${expected} olmalı (ülke kodu ve baştaki 0 olmadan)`,
        })
        return
      }
      // Doğru uzunlukta ama imkânsız başlangıç (ör. ülke kodunu numaraya yazmak: '9055511122').
      if (country.nsnStart && !country.nsnStart.re.test(digits)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['phoneNumber'],
          message: `${country.name} numarası ${country.nsnStart.hint} ile başlar — ülke kodunu numaraya yazmayın`,
        })
      }
    })

/** Tip çıkarımı için varsayılan (otel) şema — alan yapısı iki üründe de aynıdır. */
export const reservationFormSchema = makeReservationFormSchema('hotel')

export type ReservationFormValues = z.infer<typeof reservationFormSchema>
type PassengerValues = ReservationFormValues['passengers'][number]

export const emptyPassenger: PassengerValues = {
  firstName: '',
  lastName: '',
  passengerType: 'adult',
  title: '1',
  age: '',
  nationality: '',
  birthDate: '',
  identityNumber: '',
}

/**
 * Yolcu satırlarını teklifin pax'ıyla önden doldurur: otelde `adults` yetişkin + `children` çocuk
 * (çocuk yaşları aramadan), uçuşta `passengerCount` yetişkin. Uyruk aramanın uyruğuyla dolar.
 * TourVisio yolcu sayısı-teklif eşleşmesini şart koştuğundan satır sayısı burada sabitlenir.
 */
export function initialPassengers(draft: ReservationDraft): PassengerValues[] {
  if (draft.productType === 'flight') {
    const count = Math.max(1, draft.flight.passengerCount)
    return Array.from({ length: count }, () => ({ ...emptyPassenger, passengerType: 'adult' }))
  }
  const nat = (draft.hotel.nationality ?? '').toUpperCase()
  const prefill = isCountryCode(nat) ? nat : ''
  const adults = Math.max(1, draft.hotel.adults)
  const children = draft.hotel.children ?? 0
  const adultRows: PassengerValues[] = Array.from({ length: adults }, () => ({
    ...emptyPassenger,
    passengerType: 'adult',
    nationality: prefill,
  }))
  const childRows: PassengerValues[] = Array.from({ length: children }, (_, i) => ({
    ...emptyPassenger,
    passengerType: 'child',
    age: draft.childAges[i] != null ? String(draft.childAges[i]) : '',
    nationality: prefill,
  }))
  return [...adultRows, ...childRows]
}

/**
 * Telefon alanının açılış ülkesi: ana misafirin uyruğu (varsa), yoksa varsayılan. Uyruk değişince
 * form ayrıca kodu canlı günceller (ReservationFormPage).
 */
export function initialPhoneCountry(draft: ReservationDraft): string {
  const lead = initialPassengers(draft)[0]?.nationality
  return isCountryCode(lead) ? lead : DEFAULT_COUNTRY_CODE
}

/** Teklifin beklediği yolcu sayısı (yetişkin + çocuk / uçuşta yolcu sayısı). */
export function expectedTravellerCount(draft: ReservationDraft): number {
  return draft.productType === 'flight'
    ? Math.max(1, draft.flight.passengerCount)
    : Math.max(1, draft.hotel.adults) + (draft.hotel.children ?? 0)
}

/**
 * Ülke + ulusal numarayı TourVisio'nun istediği {ülke, alan, numara} üçlüsüne çevirir. Uçuş
 * biletlemesi üçünü de NON-NULL ister. Ülke kodu artık tahmin EDİLMEZ — kullanıcının seçtiği
 * ülkeden gelir (eski splitPhone her numarayı '90' sayıyordu, +49'luk bir numara Türk numarası
 * olarak gidiyordu). Alan kodu ulusal numaranın ilk 3 hanesidir.
 */
export function buildContactPhone(
  countryCode: string,
  nationalNumber: string,
): { countryCode: string; areaCode: string; phoneNumber: string } {
  const country = findCountry(countryCode)
  const digits = normalizeNationalNumber(nationalNumber, countryCode)
  return {
    countryCode: country?.dial ?? '',
    areaCode: digits.slice(0, 3),
    phoneNumber: digits.slice(3),
  }
}

/** E.164 gösterim: '+90 5551112233' → '+905551112233'. Backend düz `phone` alanına yazılır. */
export function formatE164(countryCode: string, nationalNumber: string): string {
  const country = findCountry(countryCode)
  const digits = normalizeNationalNumber(nationalNumber, countryCode)
  return country ? `+${country.dial}${digits}` : digits
}

/**
 * Form değerleri + ürün taslağını `/preview` komutuna çevirir. İlk yolcu lider'dir (iletişim ona
 * yazılır); ünvan sayıya (1/2), uyruk büyük harfe çevrilir. Ürün tipine göre yalnız ilgili blok doldurulur.
 */
export function toPreviewCommand(
  draft: ReservationDraft,
  values: ReservationFormValues,
): PreviewReservationCommand {
  const email = values.email.trim()
  const contactPhone = buildContactPhone(values.phoneCountry, values.phoneNumber)
  const phone = formatE164(values.phoneCountry, values.phoneNumber)
  const travellers: TravellerInput[] = values.passengers.map((p, index) => ({
    firstName: p.firstName.trim(),
    lastName: p.lastName.trim(),
    passengerType: p.passengerType,
    title: Number(p.title),
    age: p.age ? Number(p.age) : null,
    nationalityCode: p.nationality.toUpperCase(),
    // Uçuş biletlemesi için yolcu başına DOB + TCKN (backend mapper birebir gönderir; boşsa null).
    birthDate: p.birthDate?.trim() ? p.birthDate.trim() : null,
    identityNumber: p.identityNumber?.trim() ? p.identityNumber.trim() : null,
    leader: index === 0,
    // Lider yolcu iletişimi: backend mapper e-postayı address.email'den, telefonu yapısal
    // contactPhone'dan okur — düz email/phone'u da (uyumluluk için) taşıyoruz.
    ...(index === 0 ? { email, phone, contactPhone, address: { email } } : {}),
  }))
  const lead = values.passengers[0]
  const base = {
    currency: draft.currency,
    totalAmount: draft.price,
    leadGuestName: `${lead.firstName.trim()} ${lead.lastName.trim()}`,
    // Gidiş-dönüşte İKİ jeton gider: TourVisio bacakları ayrı fiyatlar ve ayrı jetonlarla satar,
    // yalnız gidişi göndermek tek yön bilet alırdı.
    offerIds:
      draft.productType === 'flight' && draft.returnOfferId
        ? [draft.offerId, draft.returnOfferId]
        : [draft.offerId],
    travellers,
  }
  return draft.productType === 'hotel'
    ? { ...base, hotel: draft.hotel }
    : { ...base, flight: draft.flight }
}

const CURRENCY_RE = /^[A-Za-z]{3}$/

/**
 * Önizleme isteğini GÖNDERMEDEN önce, backend Bean Validation'ını + TourVisio zorunlu alanlarını
 * (ünvan, uyruk, yolcu sayısı-teklif eşleşmesi) yansıtan istemci-tarafı kontrol. Boş dizi = geçerli.
 */
export function validatePreviewCommand(cmd: PreviewReservationCommand): string[] {
  const errors: string[] = []

  if (!cmd.leadGuestName?.trim()) errors.push('Ana misafir adı eksik.')
  if (!(cmd.totalAmount >= 0)) errors.push('Toplam tutar geçersiz.')
  if (!CURRENCY_RE.test(cmd.currency ?? '')) errors.push('Para birimi 3 harfli olmalı (ör. EUR).')
  if (!cmd.offerIds?.length || cmd.offerIds.some((o) => !o)) {
    errors.push('Ürün teklif bilgisi eksik — lütfen aramadan ürünü tekrar seçin.')
  }
  if (!cmd.travellers?.length) errors.push('En az bir yolcu girin.')
  cmd.travellers?.forEach((t, i) => {
    if (!t.firstName?.trim() || !t.lastName?.trim()) errors.push(`${i + 1}. yolcunun adı ve soyadı gerekli.`)
    if (!t.title) errors.push(`${i + 1}. yolcunun ünvanı (Bay/Bayan) gerekli.`)
    if (!t.nationalityCode?.trim()) errors.push(`${i + 1}. yolcunun uyruğu gerekli.`)
  })
  // Lider yolcu yetişkin olmalı (backend isLeadTravellerAnAdult) — iletişim ona yazılır.
  if (cmd.travellers?.[0] && cmd.travellers[0].passengerType !== 'adult') {
    errors.push('Ana misafir yetişkin olmalı.')
  }

  if (!cmd.hotel && !cmd.flight) {
    errors.push('Rezervasyon için otel ya da uçuş bilgisi gerekli — lütfen aramadan tekrar seçin.')
  }

  if (cmd.hotel) {
    const h = cmd.hotel
    if (!h.hotelName?.trim()) errors.push('Otel adı eksik.')
    if (!h.checkIn || !h.checkOut) errors.push('Giriş/çıkış tarihi eksik — lütfen aramayı güncelleyin.')
    else if (h.checkOut <= h.checkIn) errors.push('Çıkış tarihi giriş tarihinden sonra olmalı.')
    if (!(h.adults >= 1)) errors.push('En az bir yetişkin olmalı.')
    if (!(h.rooms >= 1)) errors.push('En az bir oda olmalı.')
    if (h.rooms > h.adults) {
      errors.push('Oda sayısı yetişkin sayısından fazla olamaz (her odada en az bir yetişkin).')
    }
    if (!CURRENCY_RE.test(h.currency ?? '')) errors.push('Otel para birimi geçersiz.')
    const expected = h.adults + (h.children ?? 0)
    if (cmd.travellers && cmd.travellers.length !== expected) {
      errors.push(`Yolcu sayısı aramayla eşleşmeli (${expected} kişi).`)
    }
  }

  if (cmd.flight) {
    const f = cmd.flight
    if (!f.origin?.trim() || !f.destination?.trim()) errors.push('Uçuş kalkış/varış bilgisi eksik.')
    if (!f.departTime) errors.push('Uçuş kalkış zamanı eksik.')
    if (!f.tripType) errors.push('Uçuş yön bilgisi eksik (gidiş / gidiş-dönüş) — lütfen aramayı tekrarlayın.')
    if (!(f.passengerCount >= 1)) errors.push('Uçuşta en az bir yolcu olmalı.')
    if (!CURRENCY_RE.test(f.currency ?? '')) errors.push('Uçuş para birimi geçersiz.')
    if (cmd.travellers && cmd.travellers.length !== f.passengerCount) {
      errors.push(`Yolcu sayısı aramayla eşleşmeli (${f.passengerCount} kişi).`)
    }
    // Uçuş biletlemesi TourVisio'da her yolcu için doğum tarihi + TC kimlik no, lider için de
    // e-posta ve telefon ister (setReservationInfo: ParameterCanNotBeNull). Otelde gerekmez.
    cmd.travellers?.forEach((t, i) => {
      if (!ISO_DATE_RE.test(t.birthDate ?? '')) {
        errors.push(`${i + 1}. yolcunun doğum tarihi gerekli (uçuş).`)
      }
      if (!/^\d{11}$/.test(t.identityNumber ?? '')) {
        errors.push(`${i + 1}. yolcunun TC kimlik no'su gerekli (11 hane, uçuş).`)
      }
    })
    const lead = cmd.travellers?.[0]
    if (!lead?.address?.email?.trim()) errors.push('Lider yolcu için e-posta gerekli (uçuş).')
    if (!lead?.contactPhone?.phoneNumber?.trim()) errors.push('Lider yolcu için telefon gerekli (uçuş).')
  }

  return errors
}
