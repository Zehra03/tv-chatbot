import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { motion } from 'framer-motion'
import { CheckCircle2, XCircle } from 'lucide-react'
import { AiOffBanner } from '@/features/reservation/AiOffBanner'
import { FormStepper } from '@/features/reservation/FormStepper'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { TiltedCard } from '@/components/TiltedCard'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Spinner } from '@/components/ui/spinner'
import { useAppSelector } from '@/app/hooks'
import { darkFieldClass, darkPrimaryButtonClass } from '@/lib/field-styles'
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
        <FormStepper current={3} />
        <TiltedCard rotateAmplitude={5} scaleOnHover={1.01}>
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardContent className="relative space-y-4 overflow-hidden p-8 text-center">
            {/* Yumuşak kutlama halesi + ikonda tek seferlik scale-in. */}
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-x-0 top-0 h-32 bg-gradient-to-b from-brand-teal/15 to-transparent"
            />
            <motion.div
              initial={{ scale: 0.4, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 260, damping: 18 }}
              className="relative mx-auto w-fit"
            >
              <CheckCircle2 className="h-10 w-10 text-brand-teal" aria-hidden />
            </motion.div>
            <h1 className="text-xl font-bold">Rezervasyonunuz alındı</h1>
            <div>
              <p className="text-sm text-brand-ice/70">Rezervasyon numaranız</p>
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
              <Button asChild className={darkPrimaryButtonClass}>
                <Link to={`/reservations/${reservation.id}`}>Detayı gör</Link>
              </Button>
              <Button
                asChild
                variant="outline"
                className="border-white/15 bg-white/5 text-brand-ice hover:border-brand-teal hover:bg-white/10 hover:text-white"
              >
                <Link to="/reservations">Rezervasyonlarım</Link>
              </Button>
            </div>
          </CardContent>
        </Card>
        </TiltedCard>
      </div>
    )
  }

  // Başarısızlık ekranı — hata mesajı + önizlemeye/forma dönüş.
  if (create.isError) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={3} />
        <TiltedCard rotateAmplitude={5} scaleOnHover={1.01}>
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardContent className="space-y-4 p-8 text-center">
            <XCircle className="mx-auto h-10 w-10 text-destructive" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyon oluşturulamadı</h1>
            <p role="alert" className="text-sm text-red-400">
              {create.error.message}
            </p>
            <div className="flex justify-center gap-3">
              <Button className={darkPrimaryButtonClass} onClick={() => create.reset()}>
                Önizlemeye dön
              </Button>
              <Button
                variant="ghost"
                className="text-brand-ice/80 hover:bg-white/10 hover:text-white"
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
        </TiltedCard>
      </div>
    )
  }

  if (!draft) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 py-12 text-center">
        <h1 className="text-2xl font-bold text-white">Rezervasyon</h1>
        <p className="text-sm text-brand-ice/70">
          Önce bir ürün seçmelisiniz — sohbetten ya da sonuç listelerinden bir kartta
          &quot;Seç&quot;e tıklayın.
        </p>
        <div className="flex justify-center gap-3">
          {[
            { to: '/chat', label: 'Sohbete git' },
            { to: '/hotels', label: 'Oteller' },
            { to: '/flights', label: 'Uçuşlar' },
          ].map((item) => (
            <Button
              key={item.to}
              asChild
              variant="outline"
              size="sm"
              className="border-white/15 bg-white/5 text-brand-ice hover:border-brand-teal hover:bg-white/10 hover:text-white"
            >
              <Link to={item.to}>{item.label}</Link>
            </Button>
          ))}
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
        <FormStepper current={2} />
        <h1 className="text-2xl font-bold text-white">Rezervasyon önizleme</h1>
        {/* Onay checkbox'ı + butonlar var: eğim küçük, büyüme yok. */}
        <TiltedCard rotateAmplitude={4} scaleOnHover={1}>
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardHeader>
            <CardTitle>{preview.data.title}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-brand-ice/70">{preview.data.summary}</p>
            <div>
              <p className="mb-1 text-sm font-semibold">Misafirler</p>
              <ul className="space-y-1 text-sm text-brand-ice/70">
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

            <label className="flex items-start gap-3 rounded-lg border border-brand-teal/30 bg-brand-teal/10 p-3 text-sm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
                className="mt-0.5 h-5 w-5 rounded border-white/30 accent-brand-teal"
              />
              Bilgilerimi kontrol ettim, rezervasyonu onaylıyorum.
            </label>

            <div className="flex gap-3">
              <Button
                className={darkPrimaryButtonClass}
                disabled={!confirmed || create.isPending}
                onClick={() => create.mutate(request)}
              >
                {create.isPending ? (
                  <>
                    <Spinner size={16} decorative className="text-white" />
                    Gönderiliyor…
                  </>
                ) : (
                  'Rezervasyonu onayla'
                )}
              </Button>
              <Button
                variant="ghost"
                className="text-brand-ice/80 hover:bg-white/10 hover:text-white"
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
        </TiltedCard>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <FormStepper current={1} />
      <h1 className="text-2xl font-bold text-white">Rezervasyon</h1>
      <AiOffBanner />

      <TiltedCard rotateAmplitude={5} scaleOnHover={1.01}>
      <Card className="glass-card border-white/15 bg-white/10 text-white">
        <CardHeader>
          <CardTitle>Ürün özeti</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="break-words font-semibold">{draft.title}</p>
            <p className="break-words text-sm text-brand-ice/70">{draft.summary}</p>
          </div>
          <p className="shrink-0 text-lg font-bold">{formatPrice(draft.price, draft.currency)}</p>
        </CardContent>
      </Card>
      </TiltedCard>

      <form onSubmit={handleSubmit(onValid)} className="space-y-6" noValidate>
        <section className="space-y-3">
          <h2 className="font-semibold text-white">Misafir / yolcu bilgileri</h2>
          {fields.map((field, index) => {
            const pErr = errors.passengers?.[index]
            return (
              <div
                key={field.id}
                className="space-y-3 rounded-xl border border-white/10 bg-white/5 p-4 text-white"
              >
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold">
                    {index === 0 ? 'Ana misafir' : `Yolcu ${index + 1}`}
                  </p>
                  {index > 0 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="text-brand-ice/80 hover:bg-white/10 hover:text-white"
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
                      className={darkFieldClass}
                      {...register(`passengers.${index}.firstName`)}
                    />
                    {pErr?.firstName && (
                      <p
                        id={`passenger-${index}-firstName-error`}
                        className="text-xs text-red-400"
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
                      className={darkFieldClass}
                      {...register(`passengers.${index}.lastName`)}
                    />
                    {pErr?.lastName && (
                      <p
                        id={`passenger-${index}-lastName-error`}
                        className="text-xs text-red-400"
                      >
                        {pErr.lastName.message}
                      </p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-type`}>Tip</Label>
                    <NativeSelect
                      id={`passenger-${index}-type`}
                      className={darkFieldClass}
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
                      className={darkFieldClass}
                      {...register(`passengers.${index}.age`)}
                    />
                    {pErr?.age && (
                      <p id={`passenger-${index}-age-error`} className="text-xs text-red-400">
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
                      className={darkFieldClass}
                      {...register(`passengers.${index}.nationality`)}
                    />
                    {pErr?.nationality && (
                      <p
                        id={`passenger-${index}-nationality-error`}
                        className="text-xs text-red-400"
                      >
                        {pErr.nationality.message}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="border-white/15 bg-white/5 text-brand-ice hover:border-brand-teal hover:bg-white/10 hover:text-white"
            onClick={() => append(emptyPassenger)}
          >
            Yolcu ekle
          </Button>
        </section>

        <section className="space-y-3">
          <h2 className="font-semibold text-white">İletişim</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="contact-email">E-posta</Label>
              <Input
                id="contact-email"
                type="email"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'contact-email-error' : undefined}
                className={darkFieldClass}
                {...register('email')}
              />
              {errors.email && (
                <p id="contact-email-error" className="text-xs text-red-400">
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
                className={darkFieldClass}
                {...register('phone')}
              />
              {errors.phone && (
                <p id="contact-phone-error" className="text-xs text-red-400">
                  {errors.phone.message}
                </p>
              )}
            </div>
          </div>
        </section>

        {preview.isError && (
          <p role="alert" className="text-sm text-red-400">
            {preview.error.message}
          </p>
        )}
        <Button type="submit" className={darkPrimaryButtonClass} disabled={preview.isPending}>
          {preview.isPending ? (
            <>
              <Spinner size={16} decorative className="text-white" />
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
