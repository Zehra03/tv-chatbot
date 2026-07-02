import { Link } from 'react-router-dom'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { useAppSelector } from '@/app/hooks'
import {
  emptyPassenger,
  reservationFormSchema,
  type ReservationFormValues,
} from '@/features/reservation/reservationFormSchema'
import { formatPrice } from '@/utils/format'

/**
 * /reservation/new — kontrollü rezervasyon formu (docs/frontend-architecture.md §9).
 * Ürün özeti reservationDraft'tan gelir; misafir/yolcu + iletişim alanları
 * RHF + Zod ile sınırda valide edilir. Booking'i YALNIZCA bu form yapar —
 * chatbot değil (önizleme + açık onay sonraki adımda bağlanır).
 */
export function ReservationFormPage() {
  const draft = useAppSelector((s) => s.reservationDraft.draft)

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

  // Önizleme + onay + gönderim bir sonraki adımda bağlanır.
  const onValid = (_values: ReservationFormValues) => {}

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">Rezervasyon</h1>

      <Card>
        <CardHeader>
          <CardTitle>Ürün özeti</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-4">
          <div>
            <p className="font-semibold">{draft.title}</p>
            <p className="text-sm text-muted-foreground">{draft.summary}</p>
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
                    <Button type="button" variant="ghost" size="sm" onClick={() => remove(index)}>
                      Kaldır
                    </Button>
                  )}
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-firstName`}>Ad</Label>
                    <Input
                      id={`passenger-${index}-firstName`}
                      {...register(`passengers.${index}.firstName`)}
                    />
                    {pErr?.firstName && (
                      <p className="text-xs text-destructive">{pErr.firstName.message}</p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-lastName`}>Soyad</Label>
                    <Input
                      id={`passenger-${index}-lastName`}
                      {...register(`passengers.${index}.lastName`)}
                    />
                    {pErr?.lastName && (
                      <p className="text-xs text-destructive">{pErr.lastName.message}</p>
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
                      {...register(`passengers.${index}.age`)}
                    />
                    {pErr?.age && <p className="text-xs text-destructive">{pErr.age.message}</p>}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-nationality`}>Uyruk (opsiyonel)</Label>
                    <Input
                      id={`passenger-${index}-nationality`}
                      placeholder="TR"
                      maxLength={2}
                      {...register(`passengers.${index}.nationality`)}
                    />
                    {pErr?.nationality && (
                      <p className="text-xs text-destructive">{pErr.nationality.message}</p>
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
              <Input id="contact-email" type="email" {...register('email')} />
              {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="contact-phone">Telefon</Label>
              <Input id="contact-phone" type="tel" placeholder="+90…" {...register('phone')} />
              {errors.phone && <p className="text-xs text-destructive">{errors.phone.message}</p>}
            </div>
          </div>
        </section>

        <Button type="submit">Önizlemeye geç</Button>
      </form>
    </div>
  )
}
