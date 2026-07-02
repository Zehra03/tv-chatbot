import { z } from 'zod'
import type { CreateReservationRequest } from '@/api'
import type { Passenger } from '@/types'
import type { ReservationDraft } from '@/features/reservation/reservationDraftSlice'

/**
 * Rezervasyon formu sınır validasyonu (docs/frontend-architecture.md §9,
 * CLAUDE.md: formlar Zod ile valide edilir). Yaş/uyruk opsiyoneldir (DB'de
 * nullable); iletişim bilgisi ana misafire yazılır. Sayısal alanlar form'da
 * string taşınır, gönderimde dönüştürülür.
 */
export const passengerSchema = z.object({
  firstName: z.string().trim().min(2, 'En az 2 karakter girin'),
  lastName: z.string().trim().min(2, 'En az 2 karakter girin'),
  passengerType: z.enum(['adult', 'child']),
  age: z
    .string()
    .optional()
    .refine((v) => !v || (/^\d{1,3}$/.test(v) && Number(v) <= 120), 'Geçerli bir yaş girin (0–120)'),
  nationality: z
    .string()
    .optional()
    .refine((v) => !v || /^[A-Za-z]{2}$/.test(v), 'İki harfli ülke kodu girin (ör. TR)'),
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

export const emptyPassenger: ReservationFormValues['passengers'][number] = {
  firstName: '',
  lastName: '',
  passengerType: 'adult',
  age: '',
  nationality: '',
}

/** Form değerlerini backend isteğine çevirir; iletişim ana misafire (0) yazılır. */
export function toCreateRequest(
  draft: ReservationDraft,
  values: ReservationFormValues,
): CreateReservationRequest {
  const passengers: Passenger[] = values.passengers.map((p, index) => ({
    firstName: p.firstName.trim(),
    lastName: p.lastName.trim(),
    passengerType: p.passengerType,
    age: p.age ? Number(p.age) : null,
    nationality: p.nationality ? p.nationality.toUpperCase() : undefined,
    ...(index === 0 ? { email: values.email.trim(), phone: values.phone.trim() } : {}),
  }))
  return {
    productType: draft.productType,
    productId: draft.productId,
    currency: draft.currency,
    passengers,
  }
}
