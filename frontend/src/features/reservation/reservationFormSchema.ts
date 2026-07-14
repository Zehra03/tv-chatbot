import { z } from 'zod'
import type { PreviewReservationCommand, TravellerInput } from '@/api'
import type { ReservationDraft } from '@/features/reservation/reservationDraftSlice'

/**
 * Rezervasyon formu sınır validasyonu (docs/frontend-architecture.md §9,
 * CLAUDE.md: formlar Zod ile valide edilir). TourVisio her yolcu için ünvan (Bay/Bayan) ve uyruk
 * ZORUNLU ister; ayrıca yolcu SAYISI teklifin pax'ıyla (yetişkin+çocuk) eşleşmelidir — bu yüzden
 * satırlar aramadan önden doldurulur ve tip/ sayı sabittir. Sayısal alanlar formda string taşınır.
 */

/** TourVisio ünvan kodu: 1 = Bay, 2 = Bayan. */
export const TITLE_OPTIONS = [
  { value: '1', label: 'Bay' },
  { value: '2', label: 'Bayan' },
] as const

const nationalitySchema = z
  .string()
  .trim()
  .regex(/^[A-Za-z]{2}$/, 'İki harfli ülke kodu girin (ör. TR)')

export const passengerSchema = z
  .object({
    firstName: z.string().trim().min(2, 'En az 2 karakter girin'),
    lastName: z.string().trim().min(2, 'En az 2 karakter girin'),
    passengerType: z.enum(['adult', 'child']),
    // Ünvan zorunlu (TourVisio setReservationInfo şartı).
    title: z.enum(['1', '2']),
    age: z
      .string()
      .optional()
      .refine((v) => !v || (/^\d{1,3}$/.test(v) && Number(v) <= 120), 'Geçerli bir yaş girin (0–120)'),
    // Uyruk zorunlu (TourVisio şartı); aramadan önden doldurulur.
    nationality: nationalitySchema,
  })
  .superRefine((p, ctx) => {
    if (p.passengerType === 'child' && !p.age) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['age'], message: 'Çocuk için yaş gerekli' })
    }
  })

export const reservationFormSchema = z.object({
  passengers: z.array(passengerSchema).min(1, 'En az bir yolcu girin'),
  email: z.string().trim().email('Geçerli bir e-posta girin'),
  phone: z
    .string()
    .trim()
    .regex(/^\+?[0-9 ()-]{7,20}$/, 'Geçerli bir telefon girin'),
})

export type ReservationFormValues = z.infer<typeof reservationFormSchema>
type PassengerValues = ReservationFormValues['passengers'][number]

export const emptyPassenger: PassengerValues = {
  firstName: '',
  lastName: '',
  passengerType: 'adult',
  title: '1',
  age: '',
  nationality: '',
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
  const adults = Math.max(1, draft.hotel.adults)
  const children = draft.hotel.children ?? 0
  const adultRows: PassengerValues[] = Array.from({ length: adults }, () => ({
    ...emptyPassenger,
    passengerType: 'adult',
    nationality: nat,
  }))
  const childRows: PassengerValues[] = Array.from({ length: children }, (_, i) => ({
    ...emptyPassenger,
    passengerType: 'child',
    age: draft.childAges[i] != null ? String(draft.childAges[i]) : '',
    nationality: nat,
  }))
  return [...adultRows, ...childRows]
}

/** Teklifin beklediği yolcu sayısı (yetişkin + çocuk / uçuşta yolcu sayısı). */
export function expectedTravellerCount(draft: ReservationDraft): number {
  return draft.productType === 'flight'
    ? Math.max(1, draft.flight.passengerCount)
    : Math.max(1, draft.hotel.adults) + (draft.hotel.children ?? 0)
}

/**
 * Form değerleri + ürün taslağını `/preview` komutuna çevirir. İlk yolcu lider'dir (iletişim ona
 * yazılır); ünvan sayıya (1/2), uyruk büyük harfe çevrilir. Ürün tipine göre yalnız ilgili blok doldurulur.
 */
export function toPreviewCommand(
  draft: ReservationDraft,
  values: ReservationFormValues,
): PreviewReservationCommand {
  const travellers: TravellerInput[] = values.passengers.map((p, index) => ({
    firstName: p.firstName.trim(),
    lastName: p.lastName.trim(),
    passengerType: p.passengerType,
    title: Number(p.title),
    age: p.age ? Number(p.age) : null,
    nationalityCode: p.nationality.toUpperCase(),
    leader: index === 0,
    ...(index === 0 ? { email: values.email.trim(), phone: values.phone.trim() } : {}),
  }))
  const lead = values.passengers[0]
  const base = {
    currency: draft.currency,
    totalAmount: draft.price,
    leadGuestName: `${lead.firstName.trim()} ${lead.lastName.trim()}`,
    offerIds: [draft.offerId],
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
  }

  return errors
}
