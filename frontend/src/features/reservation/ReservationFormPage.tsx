import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Spinner } from '@/components/ui/spinner'
import { useAppSelector } from '@/app/hooks'
import {
  emptyPassenger,
  reservationFormSchema,
  toCreateRequest,
  type ReservationFormValues,
} from '@/features/reservation/reservationFormSchema'
import { useReservationPreview } from '@/features/reservation/useReservationPreview'
import { useCreateReservation } from '@/features/reservation/useCreateReservation'
import {
  RESERVATION_STATUS_LABELS,
  reservationStatusVariant,
} from '@/features/reservation/status'
import type { CreateReservationRequest } from '@/api'
import { formatPrice } from '@/utils/format'

/**
 * /reservation/new — kontrollü rezervasyon formu (docs/frontend-architecture.md §9).
 * Ürün özeti reservationDraft'tan gelir; misafir/yolcu + iletişim alanları
 * RHF + Zod ile sınırda valide edilir. Booking'i YALNIZCA bu form yapar —
 * chatbot değil (önizleme + açık onay sonraki adımda bağlanır).
 */
export function ReservationFormPage() {
  const draft = useAppSelector((s) => s.reservationDraft.draft)
  const preview = useReservationPreview()
  const create = useCreateReservation()
  const [request, setRequest] = useState<CreateReservationRequest | null>(null)
  const [confirmed, setConfirmed] = useState(false)

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ReservationFormValues>({
    resolver: zodResolver(reservationFormSchema),
    defaultValues: { passengers: [emptyPassenger], email: '', phone: '' },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'passengers' })

  // Başarı ekranı — taslak başarıda temizlendiğinden no-draft kontrolünden ÖNCE.
  if (create.isSuccess && create.data) {
    const reservation = create.data
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <Card>
          <CardContent className="space-y-4 p-8 text-center">
            <CheckCircle2 className="mx-auto h-10 w-10 text-primary" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyonunuz alındı</h1>
            <div>
              <p className="text-sm text-muted-foreground">Rezervasyon numaranız</p>
              <p className="break-all font-mono text-lg font-semibold">
                {reservation.reservationNumber}
              </p>
            </div>
            <div className="flex items-center justify-center gap-3">
              <Badge variant={reservationStatusVariant(reservation.status)}>
                {RESERVATION_STATUS_LABELS[reservation.status]}
              </Badge>
              <span className="font-bold">
                {formatPrice(reservation.totalAmount, reservation.currency)}
              </span>
            </div>
            <div className="flex justify-center gap-3">
              <Button asChild>
                <Link to={`/reservations/${reservation.id}`}>Detayı gör</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/reservations">Rezervasyonlarım</Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Başarısızlık ekranı — hata mesajı + önizlemeye/forma dönüş.
  if (create.isError) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <Card>
          <CardContent className="space-y-4 p-8 text-center">
            <XCircle className="mx-auto h-10 w-10 text-destructive" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyon oluşturulamadı</h1>
            <p role="alert" className="text-sm text-destructive">
              {create.error.message}
            </p>
            <div className="flex justify-center gap-3">
              <Button onClick={() => create.reset()}>Önizlemeye dön</Button>
              <Button
                variant="ghost"
                onClick={() => {
                  create.reset()
                  preview.reset()
                }}
              >
                Forma dön
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (!draft) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 py-12 text-center">
        <h1 className="text-2xl font-bold">Rezervasyon</h1>
        <p className="text-sm text-muted-foreground">
          Önce bir ürün seçmelisiniz — sohbetten ya da sonuç listelerinden bir kartta
          &quot;Seç&quot;e tıklayın.
        </p>
        <div className="flex justify-center gap-3">
          <Button asChild variant="outline" size="sm">
            <Link to="/chat">Sohbete git</Link>
          </Button>
          <Button asChild variant="outline" size="sm">
            <Link to="/hotels">Oteller</Link>
          </Button>
          <Button asChild variant="outline" size="sm">
            <Link to="/flights">Uçuşlar</Link>
          </Button>
        </div>
      </div>
    )
  }

  const onValid = (values: ReservationFormValues) => {
    const req = toCreateRequest(draft, values)
    setRequest(req)
    setConfirmed(false)
    create.reset()
    preview.mutate(req)
  }

  // Adım 2 — önizleme + açık onay ("kontrollü rezervasyon"): checkbox
  // işaretlenmeden gönderim yapılamaz. Toplam tutar backend'in hesabıdır.
  if (preview.data && request) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <h1 className="text-2xl font-bold">Rezervasyon önizleme</h1>
        <Card>
          <CardHeader>
            <CardTitle>{preview.data.title}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">{preview.data.summary}</p>
            <div>
              <p className="mb-1 text-sm font-semibold">Misafirler</p>
              <ul className="space-y-1 text-sm text-muted-foreground">
                {preview.data.passengers.map((p, i) => (
                  <li key={`${p.firstName}-${p.lastName}-${i}`}>
                    {p.firstName} {p.lastName} —{' '}
                    {p.passengerType === 'adult' ? 'Yetişkin' : 'Çocuk'}
                    {p.email ? ` · ${p.email}` : ''}
                  </li>
                ))}
              </ul>
            </div>
            <p className="text-lg font-bold">
              Toplam: {formatPrice(preview.data.totalAmount, preview.data.currency)}
            </p>

            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-input accent-primary"
              />
              Bilgilerimi kontrol ettim, rezervasyonu onaylıyorum.
            </label>

            <div className="flex gap-3">
              <Button
                disabled={!confirmed || create.isPending}
                onClick={() => create.mutate(request)}
              >
                {create.isPending ? (
                  <>
                    <Spinner size={16} decorative className="text-primary-foreground" />
                    Gönderiliyor…
                  </>
                ) : (
                  'Rezervasyonu onayla'
                )}
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  preview.reset()
                  create.reset()
                }}
              >
                Forma dön
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">Rezervasyon</h1>

      <Card>
        <CardHeader>
          <CardTitle>Ürün özeti</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="break-words font-semibold">{draft.title}</p>
            <p className="break-words text-sm text-muted-foreground">{draft.summary}</p>
          </div>
          <p className="shrink-0 text-lg font-bold">{formatPrice(draft.price, draft.currency)}</p>
        </CardContent>
      </Card>

      <form onSubmit={handleSubmit(onValid)} className="space-y-6" noValidate>
        <section className="space-y-3">
          <h2 className="font-semibold">Misafir / yolcu bilgileri</h2>
          {fields.map((field, index) => {
            const pErr = errors.passengers?.[index]
            return (
              <div key={field.id} className="space-y-3 rounded-lg border p-4">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold">
                    {index === 0 ? 'Ana misafir' : `Yolcu ${index + 1}`}
                  </p>
                  {index > 0 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      aria-label={`Yolcu ${index + 1} kaldır`}
                      onClick={() => remove(index)}
                    >
                      Kaldır
                    </Button>
                  )}
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-firstName`}>Ad</Label>
                    <Input
                      id={`passenger-${index}-firstName`}
                      aria-invalid={!!pErr?.firstName}
                      aria-describedby={
                        pErr?.firstName ? `passenger-${index}-firstName-error` : undefined
                      }
                      {...register(`passengers.${index}.firstName`)}
                    />
                    {pErr?.firstName && (
                      <p
                        id={`passenger-${index}-firstName-error`}
                        className="text-xs text-destructive"
                      >
                        {pErr.firstName.message}
                      </p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-lastName`}>Soyad</Label>
                    <Input
                      id={`passenger-${index}-lastName`}
                      aria-invalid={!!pErr?.lastName}
                      aria-describedby={
                        pErr?.lastName ? `passenger-${index}-lastName-error` : undefined
                      }
                      {...register(`passengers.${index}.lastName`)}
                    />
                    {pErr?.lastName && (
                      <p
                        id={`passenger-${index}-lastName-error`}
                        className="text-xs text-destructive"
                      >
                        {pErr.lastName.message}
                      </p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-type`}>Tip</Label>
                    <NativeSelect
                      id={`passenger-${index}-type`}
                      {...register(`passengers.${index}.passengerType`)}
                    >
                      <option value="adult">Yetişkin</option>
                      <option value="child">Çocuk</option>
                    </NativeSelect>
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-age`}>Yaş (opsiyonel)</Label>
                    <Input
                      id={`passenger-${index}-age`}
                      inputMode="numeric"
                      aria-invalid={!!pErr?.age}
                      aria-describedby={pErr?.age ? `passenger-${index}-age-error` : undefined}
                      {...register(`passengers.${index}.age`)}
                    />
                    {pErr?.age && (
                      <p id={`passenger-${index}-age-error`} className="text-xs text-destructive">
                        {pErr.age.message}
                      </p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-nationality`}>Uyruk (opsiyonel)</Label>
                    <Input
                      id={`passenger-${index}-nationality`}
                      placeholder="TR"
                      maxLength={2}
                      aria-invalid={!!pErr?.nationality}
                      aria-describedby={
                        pErr?.nationality ? `passenger-${index}-nationality-error` : undefined
                      }
                      {...register(`passengers.${index}.nationality`)}
                    />
                    {pErr?.nationality && (
                      <p
                        id={`passenger-${index}-nationality-error`}
                        className="text-xs text-destructive"
                      >
                        {pErr.nationality.message}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
          <Button type="button" variant="outline" size="sm" onClick={() => append(emptyPassenger)}>
            Yolcu ekle
          </Button>
        </section>

        <section className="space-y-3">
          <h2 className="font-semibold">İletişim</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="contact-email">E-posta</Label>
              <Input
                id="contact-email"
                type="email"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'contact-email-error' : undefined}
                {...register('email')}
              />
              {errors.email && (
                <p id="contact-email-error" className="text-xs text-destructive">
                  {errors.email.message}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="contact-phone">Telefon</Label>
              <Input
                id="contact-phone"
                type="tel"
                placeholder="+90…"
                aria-invalid={!!errors.phone}
                aria-describedby={errors.phone ? 'contact-phone-error' : undefined}
                {...register('phone')}
              />
              {errors.phone && (
                <p id="contact-phone-error" className="text-xs text-destructive">
                  {errors.phone.message}
                </p>
              )}
            </div>
          </div>
        </section>

        {preview.isError && (
          <p role="alert" className="text-sm text-destructive">
            {preview.error.message}
          </p>
        )}
        <Button type="submit" disabled={preview.isPending}>
          {preview.isPending ? (
            <>
              <Spinner size={16} decorative className="text-primary-foreground" />
              Önizleme hazırlanıyor…
            </>
          ) : (
            'Önizlemeye geç'
          )}
        </Button>
      </form>
    </div>
  )
}
