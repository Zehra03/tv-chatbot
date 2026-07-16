import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Controller, useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertTriangle, CheckCircle2, Clock, XCircle } from 'lucide-react'
import { AiOffBanner } from '@/features/reservation/AiOffBanner'
import { FormStepper } from '@/features/reservation/FormStepper'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { Spinner } from '@/components/ui/spinner'
import { useAppSelector } from '@/app/hooks'
import { darkFieldClass } from '@/lib/field-styles'
import {
  emptyPassenger,
  initialPassengers,
  reservationFormSchema,
  TITLE_OPTIONS,
  toPreviewCommand,
  validatePreviewCommand,
  type ReservationFormValues,
} from '@/features/reservation/reservationFormSchema'
import { useReservationPreview } from '@/features/reservation/useReservationPreview'
import { useConfirmReservation } from '@/features/reservation/useConfirmReservation'
import type { ApiError, NeedsConfirmationResponse, PreviewReservationCommand } from '@/api'
import { formatDate, formatDateTime, formatPrice } from '@/utils/format'

/**
 * Kesin onaydaki backend hata kodunu (ApiError.code = OutcomeResponse.outcome) kullanıcı mesajına
 * çevirir. Bilinmeyen kodda backend mesajına düşer.
 */
function confirmErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'PREVIEW_EXPIRED':
      return 'Önizleme süresi doldu. Lütfen formu tekrar gönderip yeniden onaylayın.'
    case 'DUPLICATE_IN_PROGRESS':
      return 'Bu rezervasyon zaten onaylanıyor. Lütfen biraz bekleyip rezervasyonlarınızı kontrol edin.'
    case 'OWNERSHIP_MISMATCH':
      return 'Bu önizleme başka bir kullanıcıya ait.'
    case 'TOURVISIO_REJECTED':
      return error.message || 'Rezervasyon sağlayıcı tarafından reddedildi.'
    case 'TOURVISIO_UNAVAILABLE':
      return 'Rezervasyon sağlayıcısına şu an ulaşılamıyor. Lütfen birazdan tekrar deneyin.'
    case 'ORPHANED_BOOKING':
      return 'Rezervasyon oluşturuldu ancak kaydında bir sorun oluştu. Lütfen rezervasyonlarınızı kontrol edin.'
    default:
      return error.message
  }
}

/** Önizleme (preview) isteğinin hata kodunu kullanıcı mesajına çevirir. Sunucu tarafı doğrulama
 * (400) çoğu zaman gövdede alan detayı taşımaz; istemci ön-kontrolü (validatePreviewCommand) sorunu
 * zaten yakalar, bu yalnız ağ/sunucu kaynaklı hatalar için okunur bir mesajdır. */
function previewErrorMessage(error: ApiError): string {
  if (error.status === 400) {
    return 'Rezervasyon bilgileri sunucu tarafından reddedildi. Lütfen bilgilerinizi kontrol edin ya da aramayı yenileyip ürünü tekrar seçin.'
  }
  if (error.status === 401) return 'Bu işlem için giriş yapmanız gerekiyor.'
  return error.message
}

/**
 * /reservation/new — kontrollü rezervasyon formu (docs/frontend-architecture.md §9).
 * Ürün özeti + snapshot reservationDraft'tan gelir; misafir/yolcu + iletişim alanları RHF + Zod ile
 * sınırda valide edilir. Stateful akış: form → /preview (previewId) → açık onay → /reservations
 * (kesin onay, TourVisio). Booking'i YALNIZCA bu form yapar — chatbot değil. Uyarı (çift rezervasyon)
 * gelirse ikinci bir açık onay istenir; belirsiz/başarısız sonuçlar ayrı ekranlarda gösterilir.
 */
export function ReservationFormPage() {
  const draft = useAppSelector((s) => s.reservationDraft.draft)
  const navigate = useNavigate()
  const preview = useReservationPreview()
  const confirm = useConfirmReservation()
  const [request, setRequest] = useState<PreviewReservationCommand | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  // Uyarı (NeedsUserConfirmation) yerel state'te tutulur ki ikinci onay in-flight iken (confirm.data
  // sıfırlanınca) ekran kaybolmasın.
  const [warning, setWarning] = useState<NeedsConfirmationResponse | null>(null)
  // İstemci ön-kontrol uyarıları — önizleme isteği gönderilmeden gösterilir (backend 400'den önce).
  const [validationErrors, setValidationErrors] = useState<string[]>([])

  useEffect(() => {
    if (confirm.data?.kind === 'needsConfirmation') {
      setWarning({ confirmationToken: confirm.data.confirmationToken, warnings: confirm.data.warnings })
    } else if (confirm.data?.kind === 'created' || confirm.data?.kind === 'createdFallback') {
      setWarning(null)
    }
  }, [confirm.data])

  // Kesin onay başarılı → kullanıcıyı kalıcı (URL'li) rezervasyon sayfasına taşı. Eski satır-içi
  // başarı ekranı geçici state'e bağlıydı (React Query mutation sonucu + Redux taslak; taslak
  // KALICI DEĞİL). Canlı backend'de yavaş TourVisio commit'i sırasında bir remount/sayfa yenilemesi
  // bu state'i düşürüp kullanıcıyı boş forma ("Önce bir ürün seçmelisiniz") atabiliyordu. Detay
  // sayfası rezervasyonu backend'den okur ve yenilemeye dayanır. replace: geri tuşuyla forma dönülmez;
  // state.justBooked: detay sayfası tek seferlik "alındı" bandını gösterir (toast'a ek).
  useEffect(() => {
    if (confirm.data?.kind === 'created') {
      navigate(`/reservations/${confirm.data.reservation.id}`, {
        replace: true,
        state: { justBooked: true },
      })
    } else if (confirm.data?.kind === 'createdFallback') {
      // Özet yeniden okunamadı (id yok) → listeye götür; onay toast'ı kullanıcıyı zaten bilgilendirir.
      navigate('/reservations', { replace: true, state: { justBooked: true } })
    }
  }, [confirm.data, navigate])

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ReservationFormValues>({
    resolver: zodResolver(reservationFormSchema),
    // Yolcu satırları teklifin pax'ına göre önden doldurulur (TourVisio sayı-eşleşmesi şart).
    defaultValues: {
      passengers: draft ? initialPassengers(draft) : [emptyPassenger],
      email: '',
      phone: '',
    },
  })
  const { fields } = useFieldArray({ control, name: 'passengers' })

  const resetAll = () => {
    confirm.reset()
    preview.reset()
    setWarning(null)
  }

  // Kesin onay başarılı — yukarıdaki useEffect kullanıcıyı kalıcı rezervasyon sayfasına yönlendirir.
  // Bu erken dönüş, yönlendirme gerçekleşene kadarki tek render'da no-draft ekranının görünmemesi
  // içindir (taslak onSuccess'te temizlenmiş olabilir). Yönlendirme herhangi bir sebeple gecikirse
  // elle geçiş linki sunulur. no-draft kontrolünden ÖNCE gelir.
  if (confirm.data?.kind === 'created' || confirm.data?.kind === 'createdFallback') {
    const detailTo =
      confirm.data.kind === 'created'
        ? `/reservations/${confirm.data.reservation.id}`
        : '/reservations'
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={3} />
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardContent className="space-y-4 p-8 text-center">
            <CheckCircle2 className="mx-auto h-10 w-10 text-brand-teal" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyonunuz alındı</h1>
            <p className="flex items-center justify-center gap-2 text-sm text-brand-ice/70">
              <Spinner size={16} decorative className="text-brand-ice/70" />
              Rezervasyonunuza yönlendiriliyorsunuz…
            </p>
            <Button asChild variant="outline">
              <Link to={detailTo}>Rezervasyonuma git</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Kesin onay hatası — mesaj koda göre eşlenir; önizlemeye/forma dönüş.
  if (confirm.isError) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={3} />
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardContent className="space-y-4 p-8 text-center">
            <XCircle className="mx-auto h-10 w-10 text-destructive" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyon oluşturulamadı</h1>
            <p role="alert" className="text-sm text-red-400">
              {confirmErrorMessage(confirm.error)}
            </p>
            <div className="flex justify-center gap-3">
              <Button
                onClick={() => {
                  confirm.reset()
                  setWarning(null)
                }}
              >
                Önizlemeye dön
              </Button>
              <Button
                variant="ghost"
                className="text-brand-ice/80 hover:bg-white/10 hover:text-white"
                onClick={resetAll}
              >
                Forma dön
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Uyarı ekranı (ör. çift rezervasyon) — ikinci açık onay istenir.
  if (warning) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={2} />
        <Card className="glass-card border-amber-400/30 bg-amber-400/10 text-white">
          <CardHeader className="flex-row items-center gap-3 space-y-0">
            <AlertTriangle className="h-6 w-6 shrink-0 text-amber-400" aria-hidden />
            <CardTitle>Onayınız gerekiyor</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-brand-ice/80">
              Rezervasyon sağlayıcısı bu işlemle ilgili bir uyarı bildirdi. Devam etmek için lütfen
              onaylayın:
            </p>
            <ul className="list-disc space-y-1 pl-5 text-sm text-amber-100">
              {warning.warnings.length > 0 ? (
                warning.warnings.map((w, i) => <li key={`${w}-${i}`}>{w}</li>)
              ) : (
                <li>Aynı ürün için mevcut bir rezervasyon bulunmuş olabilir.</li>
              )}
            </ul>
            <div className="flex gap-3">
              <Button
                disabled={confirm.isPending}
                onClick={() => confirm.mutate({ confirmationToken: warning.confirmationToken })}
              >
                {confirm.isPending ? (
                  <>
                    <Spinner size={16} decorative className="text-white" />
                    Gönderiliyor…
                  </>
                ) : (
                  'Yine de onayla'
                )}
              </Button>
              <Button
                variant="ghost"
                className="text-brand-ice/80 hover:bg-white/10 hover:text-white"
                onClick={resetAll}
              >
                Vazgeç
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Belirsiz sonuç (202) — satın alma gerçekleşmiş olabilir; kullanıcı listeyi kontrol etmeli.
  if (confirm.data?.kind === 'pending') {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={3} />
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardContent className="space-y-4 p-8 text-center">
            <Clock className="mx-auto h-10 w-10 text-amber-400" aria-hidden />
            <h1 className="text-xl font-bold">Sonuç doğrulanıyor</h1>
            <p className="text-sm text-brand-ice/70">
              Rezervasyonunuz işleme alındı ancak sonucu henüz kesinleşmedi. Lütfen birazdan
              rezervasyonlarınızı kontrol edin.
            </p>
            <Button asChild>
              <Link to="/reservations">Rezervasyonlarım</Link>
            </Button>
          </CardContent>
        </Card>
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
            <Button key={item.to} asChild variant="outline" size="sm">
              <Link to={item.to}>{item.label}</Link>
            </Button>
          ))}
        </div>
      </div>
    )
  }

  const onValid = (values: ReservationFormValues) => {
    const command = toPreviewCommand(draft, values)
    // Backend Bean Validation'ını yansıtan ön-kontrol: geçersizse istek gönderilmez, kullanıcı uyarılır.
    const problems = validatePreviewCommand(command)
    if (problems.length > 0) {
      setValidationErrors(problems)
      return
    }
    setValidationErrors([])
    setRequest(command)
    setConfirmed(false)
    setWarning(null)
    confirm.reset()
    preview.mutate(command)
  }

  // Adım 2 — önizleme + açık onay ("kontrollü rezervasyon"): checkbox işaretlenmeden gönderilemez.
  // Toplam tutar backend'in dondurduğu önizlemeden gelir; misafirler passengerNames'ten.
  if (preview.data && request) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={2} />
        <h1 className="text-2xl font-bold text-white">Rezervasyon önizleme</h1>
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardHeader>
            <CardTitle>{draft.title}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-brand-ice/70">{draft.summary}</p>

            {/* Rezervasyon snapshot'ı — kullanıcı onaydan önce gerçek booking verisini görür. */}
            {draft.productType === 'hotel' ? (
              <dl className="grid gap-x-6 gap-y-2 rounded-lg border border-white/10 bg-white/5 p-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-brand-ice/60">Giriş / çıkış</dt>
                  <dd className="font-medium">
                    {formatDate(draft.hotel.checkIn)} — {formatDate(draft.hotel.checkOut)}
                  </dd>
                </div>
                <div>
                  <dt className="text-brand-ice/60">Oda / kişi</dt>
                  <dd className="font-medium">
                    {draft.hotel.rooms} oda · {draft.hotel.adults} yetişkin
                    {draft.hotel.children ? ` · ${draft.hotel.children} çocuk` : ''}
                  </dd>
                </div>
                {draft.hotel.boardType && (
                  <div>
                    <dt className="text-brand-ice/60">Pansiyon</dt>
                    <dd className="font-medium">{draft.hotel.boardType}</dd>
                  </div>
                )}
              </dl>
            ) : (
              <dl className="grid gap-x-6 gap-y-2 rounded-lg border border-white/10 bg-white/5 p-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-brand-ice/60">Rota</dt>
                  <dd className="font-medium">
                    {draft.flight.origin} → {draft.flight.destination}
                  </dd>
                </div>
                <div>
                  <dt className="text-brand-ice/60">Kalkış</dt>
                  <dd className="font-medium">{formatDateTime(draft.flight.departTime)}</dd>
                </div>
                <div>
                  <dt className="text-brand-ice/60">Yolcu</dt>
                  <dd className="font-medium">{draft.flight.passengerCount}</dd>
                </div>
              </dl>
            )}

            <div>
              <p className="mb-1 text-sm font-semibold">Misafirler</p>
              <ul className="space-y-1 text-sm text-brand-ice/70">
                {preview.data.passengerNames.map((name, i) => (
                  <li key={`${name}-${i}`}>{name}</li>
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
                disabled={!confirmed || confirm.isPending}
                onClick={() => confirm.mutate({ previewId: preview.data!.previewId })}
              >
                {confirm.isPending ? (
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
                  confirm.reset()
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
      <FormStepper current={1} />
      <h1 className="text-2xl font-bold text-white">Rezervasyon</h1>
      <AiOffBanner />

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

      <form onSubmit={handleSubmit(onValid)} className="space-y-6" noValidate>
        <section className="space-y-3">
          <h2 className="font-semibold text-white">Misafir / yolcu bilgileri</h2>
          {/* Yolcu sayısı ve tipi aramanızla eşleşir (TourVisio şartı); ünvan ve uyruk zorunludur. */}
          <p className="text-xs text-brand-ice/60">
            Yolcu sayısı ve tipi aramanızla eşleşir. Ünvan ve uyruk zorunludur.
          </p>
          {fields.map((field, index) => {
            const pErr = errors.passengers?.[index]
            const isChild = field.passengerType === 'child'
            return (
              <div
                key={field.id}
                className="space-y-3 rounded-xl border border-white/10 bg-white/5 p-4 text-white"
              >
                {/* passengerType düzenlenmez (teklif eşleşmesi) — gizli input ile gönderilir, rozet gösterir. */}
                <input type="hidden" {...register(`passengers.${index}.passengerType`)} />
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold">
                    {index === 0 ? 'Ana misafir' : `Yolcu ${index + 1}`}
                  </p>
                  <Badge variant={isChild ? 'secondary' : 'glass'}>
                    {isChild ? 'Çocuk' : 'Yetişkin'}
                  </Badge>
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-title`}>Ünvan</Label>
                    <Controller
                      control={control}
                      name={`passengers.${index}.title`}
                      render={({ field: titleField }) => (
                        <DropdownSelect
                          id={`passenger-${index}-title`}
                          value={titleField.value}
                          options={[...TITLE_OPTIONS]}
                          onChange={titleField.onChange}
                        />
                      )}
                    />
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-nationality`}>Uyruk</Label>
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
                    <Label htmlFor={`passenger-${index}-age`}>
                      {isChild ? 'Yaş' : 'Yaş (opsiyonel)'}
                    </Label>
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
                  {/* Uçuş biletlemesi TourVisio'da doğum tarihi + TC kimlik no ister (otelde gerekmez). */}
                  {draft.productType === 'flight' && (
                    <>
                      <div className="grid gap-1.5">
                        <Label htmlFor={`passenger-${index}-birthDate`}>Doğum tarihi</Label>
                        <Input
                          id={`passenger-${index}-birthDate`}
                          type="date"
                          aria-invalid={!!pErr?.birthDate}
                          aria-describedby={
                            pErr?.birthDate ? `passenger-${index}-birthDate-error` : undefined
                          }
                          className={darkFieldClass}
                          {...register(`passengers.${index}.birthDate`)}
                        />
                        {pErr?.birthDate && (
                          <p
                            id={`passenger-${index}-birthDate-error`}
                            className="text-xs text-red-400"
                          >
                            {pErr.birthDate.message}
                          </p>
                        )}
                      </div>
                      <div className="grid gap-1.5">
                        <Label htmlFor={`passenger-${index}-identityNumber`}>TC kimlik no</Label>
                        <Input
                          id={`passenger-${index}-identityNumber`}
                          inputMode="numeric"
                          maxLength={11}
                          placeholder="11 haneli"
                          aria-invalid={!!pErr?.identityNumber}
                          aria-describedby={
                            pErr?.identityNumber
                              ? `passenger-${index}-identityNumber-error`
                              : undefined
                          }
                          className={darkFieldClass}
                          {...register(`passengers.${index}.identityNumber`)}
                        />
                        {pErr?.identityNumber && (
                          <p
                            id={`passenger-${index}-identityNumber-error`}
                            className="text-xs text-red-400"
                          >
                            {pErr.identityNumber.message}
                          </p>
                        )}
                      </div>
                    </>
                  )}
                </div>
              </div>
            )
          })}
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

        {validationErrors.length > 0 && (
          <div
            role="alert"
            className="space-y-1 rounded-lg border border-amber-400/30 bg-amber-400/10 p-3 text-sm text-amber-100"
          >
            <p className="font-semibold text-amber-200">Devam etmeden önce şunları düzeltin:</p>
            <ul className="list-disc space-y-0.5 pl-5">
              {validationErrors.map((msg, i) => (
                <li key={`${msg}-${i}`}>{msg}</li>
              ))}
            </ul>
          </div>
        )}
        {preview.isError && (
          <p role="alert" className="text-sm text-red-400">
            {previewErrorMessage(preview.error)}
          </p>
        )}
        <Button type="submit" disabled={preview.isPending}>
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
