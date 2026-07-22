import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertTriangle, CheckCircle2, Clock, XCircle } from 'lucide-react'
import { AiOffBanner } from '@/features/reservation/AiOffBanner'
import { FormStepper } from '@/features/reservation/FormStepper'
import { GuestCheckoutChoice } from '@/features/reservation/GuestCheckoutChoice'
import { GuestPnrPanel } from '@/features/reservation/GuestPnrPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CountrySelect } from '@/components/ui/country-select'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { Spinner } from '@/components/ui/spinner'
import { useAppSelector } from '@/app/hooks'
import { darkFieldClass } from '@/lib/field-styles'
import { cn } from '@/lib/utils'
import { dialCodeOf, isCountryCode, parseNationalNumber } from '@/lib/countries'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import {
  emptyPassenger,
  initialPassengers,
  initialPhoneCountry,
  makeReservationFormSchema,
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
 * Misafir rezervasyonu sunucu tarafında reddedildiğinde gösterilen mesaj. Backend
 * `/api/v1/reservations/**` yolunu USER/ADMIN'e kilitlediği sürece (SecurityConfig) misafir onayı
 * 401 döner; misafir akışı MSW'ye karşı uçtan uca çalışır, gerçek backend bu izni açana kadar
 * kullanıcı burada çıkmaza değil giriş yoluna yönlendirilir.
 */
const GUEST_BOOKING_BLOCKED =
  'Misafir rezervasyonu şu an sunucu tarafından kabul edilmiyor. Lütfen giriş yapın ya da ' +
  'ücretsiz bir hesap oluşturun — seçtiğiniz ürün korunur, kaldığınız yerden devam edersiniz.'

/** Hata, misafir booking'inin sunucuda kapalı olmasından mı kaynaklanıyor? */
function isGuestBlocked(error: ApiError | null, hasAccount: boolean): boolean {
  return !hasAccount && (error?.status === 401 || error?.status === 403)
}

/**
 * Kesin onaydaki backend hata kodunu (ApiError.code = OutcomeResponse.outcome) kullanıcı mesajına
 * çevirir. Bilinmeyen kodda backend mesajına düşer.
 */
function confirmErrorMessage(error: ApiError, hasAccount: boolean): string {
  // Misafir + 401/403: backend booking'i hesaba kilitliyor (SecurityConfig). Ham "Unauthorized"
  // yerine yapılabilecek şeyi söyle — seçilen ürün taslakta durduğu için giriş sonrası akış sürer.
  if (!hasAccount && (error.status === 401 || error.status === 403)) return GUEST_BOOKING_BLOCKED
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
      // Bilinmeyen kodda apiErrorMessage'a düş: ham `error.message` ağ kopmasında/500'de
      // Türkçe arayüze "Network Error" ya da "Request failed with status code 500" basıyordu.
      // apiErrorMessage backend'in anlamlı mesajını KORUR, yalnız taşıma metnini çevirir.
      return apiErrorMessage(error)
  }
}

/** Önizleme (preview) isteğinin hata kodunu kullanıcı mesajına çevirir. Sunucu tarafı doğrulama
 * (400) çoğu zaman gövdede alan detayı taşımaz; istemci ön-kontrolü (validatePreviewCommand) sorunu
 * zaten yakalar, bu yalnız ağ/sunucu kaynaklı hatalar için okunur bir mesajdır. */
function previewErrorMessage(error: ApiError, hasAccount: boolean): string {
  if (error.status === 400) {
    return 'Rezervasyon bilgileri sunucu tarafından reddedildi. Lütfen bilgilerinizi kontrol edin ya da aramayı yenileyip ürünü tekrar seçin.'
  }
  if (error.status === 401 || error.status === 403) {
    return hasAccount ? 'Bu işlem için giriş yapmanız gerekiyor.' : GUEST_BOOKING_BLOCKED
  }
  return apiErrorMessage(error)
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
  const user = useAppSelector((s) => s.auth.user)
  // Hesabı olan kullanıcı 0. adımı hiç görmez; misafir (ve oturumsuz test/derin-bağlantı durumu)
  // önce "Giriş yap / Misafir devam et" seçimini yapar. RequireAccount ile aynı tanım.
  const hasAccount = !!user && !user.guest
  const [guestFlowAccepted, setGuestFlowAccepted] = useState(false)
  const navigate = useNavigate()
  const preview = useReservationPreview()
  const confirm = useConfirmReservation()
  const [request, setRequest] = useState<PreviewReservationCommand | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  // Fiyat değiştiyse istenen AYRI kabul (K21) — genel onay checkbox'ından bilinçli olarak ayrı.
  const [priceAccepted, setPriceAccepted] = useState(false)
  // Uyarı (NeedsUserConfirmation) yerel state'te tutulur ki ikinci onay in-flight iken (confirm.data
  // sıfırlanınca) ekran kaybolmasın.
  const [warning, setWarning] = useState<NeedsConfirmationResponse | null>(null)
  // İstemci ön-kontrol uyarıları — önizleme isteği gönderilmeden gösterilir (backend 400'den önce).
  const [validationErrors, setValidationErrors] = useState<string[]>([])

  // Uyarı YALNIZCA needsConfirmation'da yaşar; diğer HER sonuç onu düşürür. Eskiden yalnız
  // created/createdFallback temizliyordu, yani uyarıdan sonra gelen 202 (`pending`) uyarıyı
  // ayakta bırakıyordu: aşağıdaki `warning` dalı `pending` dalından önce geldiği için kullanıcı
  // "Yine de onayla" ekranına geri düşüyor ve booking ZATEN geçmiş olabilecekken ikinci kez
  // onaylamaya davet ediliyordu. `else` (kind listelemek yerine) yeni bir sonuç tipi eklendiğinde
  // de uyarının takılı kalmamasını garantiler.
  useEffect(() => {
    if (confirm.data?.kind === 'needsConfirmation') {
      setWarning({ confirmationToken: confirm.data.confirmationToken, warnings: confirm.data.warnings })
    } else if (confirm.data) {
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
    // Misafir bu sayfalara GİREMEZ (RequireAccount + backend USER/ADMIN kısıtı): yönlendirmek onu
    // /login'e düşürür ve PNR'ını hiç göremez. Sonucu satır içi GuestPnrPanel gösterir.
    if (!hasAccount) return
    if (confirm.data?.kind === 'created') {
      navigate(`/reservations/${confirm.data.reservation.id}`, {
        replace: true,
        state: { justBooked: true },
      })
    } else if (confirm.data?.kind === 'createdFallback') {
      // Özet yeniden okunamadı (id yok) → listeye götür; onay toast'ı kullanıcıyı zaten bilgilendirir.
      navigate('/reservations', { replace: true, state: { justBooked: true } })
    }
  }, [confirm.data, navigate, hasAccount])

  // Şema ürüne göre kurulur: uçuşta doğum tarihi + kimlik no ZORUNLU olur ve hata artık alanın
  // yanında görünür (eskiden yalnız validatePreviewCommand'in toplu listesinde çıkıyordu).
  const productType = draft?.productType ?? 'hotel'
  const schema = useMemo(() => makeReservationFormSchema(productType), [productType])

  const {
    register,
    control,
    handleSubmit,
    setValue,
    trigger,
    formState: { errors, isSubmitted },
  } = useForm<ReservationFormValues>({
    resolver: zodResolver(schema),
    // Blur'da doğrula (§8): alandan çıkınca hata görünsün, her tuş vuruşunda değil
    // (yazarken "geçersiz e-posta" demek saldırgandır); submit'te hepsi doğrulanır.
    // onTouched = ilk blur'dan sonra onChange ile takip eder.
    mode: 'onTouched',
    // Yolcu satırları teklifin pax'ına göre önden doldurulur (TourVisio sayı-eşleşmesi şart).
    defaultValues: {
      passengers: draft ? initialPassengers(draft) : [emptyPassenger],
      email: '',
      phoneCountry: draft ? initialPhoneCountry(draft) : '',
      phoneNumber: '',
    },
  })
  const { fields } = useFieldArray({ control, name: 'passengers' })

  // Telefon ülke kodu ana misafirin uyruğunu izler: uyruk değişince kod (ör. TR → +90, DE → +49)
  // kendiliğinden güncellenir. Kullanıcı isterse kodu sonradan ayrıca değiştirebilir (yurt dışında
  // yaşayan bir TC vatandaşının numarası başka ülkeden olabilir) — bu yüzden alan kilitli değil,
  // yalnız uyruk DEĞİŞTİĞİ anda senkronlanır.
  const leadNationality = useWatch({ control, name: 'passengers.0.nationality' })
  const phoneCountry = useWatch({ control, name: 'phoneCountry' })
  const previousLeadNationality = useRef(leadNationality)
  useEffect(() => {
    if (leadNationality === previousLeadNationality.current) return
    previousLeadNationality.current = leadNationality
    if (!isCountryCode(leadNationality)) return
    setValue('phoneCountry', leadNationality.toUpperCase())
    // Numaranın hane kuralı ülkeye bağlı — gönderim denendiyse hatayı yeni ülkeye göre tazele.
    if (isSubmitted) void trigger(['phoneCountry', 'phoneNumber'])
  }, [leadNationality, setValue, trigger, isSubmitted])

  const resetAll = () => {
    confirm.reset()
    preview.reset()
    setWarning(null)
  }

  // MİSAFİR sonucu (3. adım) — hesabı olmayan kullanıcı /reservations/:id'ye giremediği için PNR,
  // bilet özeti ve "e-postanıza gönderildi" bilgisi burada, satır içinde gösterilir. Hesap dalından
  // ÖNCE gelir. `request` onaydan önce dondurulan komuttur; taslak başarıda temizlendiği için özetin
  // tek kaynağı odur. Pratikte hep dolu (onay ancak önizlemeden sonra çağrılabilir) — yoksa da
  // rezervasyonun oluştuğunu saklamayız, yalnız özetsiz anlatırız.
  if (
    !hasAccount &&
    (confirm.data?.kind === 'created' || confirm.data?.kind === 'createdFallback')
  ) {
    const reservation = confirm.data.kind === 'created' ? confirm.data.reservation : null
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={3} />
        {reservation && request ? (
          <GuestPnrPanel reservation={reservation} command={request} />
        ) : (
          <Card className="border-border bg-card text-foreground">
            <CardContent className="space-y-4 p-8 text-center">
              <CheckCircle2 className="mx-auto h-10 w-10 text-success" aria-hidden />
              <h1 className="text-xl font-bold">Rezervasyonunuz tamamlandı</h1>
              <p className="text-sm text-muted-foreground">
                Rezervasyonunuz oluşturuldu ancak özeti şu an görüntülenemiyor. PNR bilgisi e-posta
                adresinize gönderilmiştir.
              </p>
              <Button asChild variant="cta">
                <Link to="/chat">Sohbete dön</Link>
              </Button>
            </CardContent>
          </Card>
        )}
      </div>
    )
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
        <Card className="glass-card border-border bg-card text-foreground">
          <CardContent className="space-y-4 p-8 text-center">
            <CheckCircle2 className="mx-auto h-10 w-10 text-primary" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyonunuz alındı</h1>
            <p className="flex items-center justify-center gap-2 text-sm text-muted-foreground">
              <Spinner size={16} decorative className="text-muted-foreground" />
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
        <Card className="glass-card border-border bg-card text-foreground">
          <CardContent className="space-y-4 p-8 text-center">
            <XCircle className="mx-auto h-10 w-10 text-destructive-emphasis" aria-hidden />
            <h1 className="text-xl font-bold">Rezervasyon oluşturulamadı</h1>
            <p role="alert" className="text-sm text-destructive-emphasis">
              {confirmErrorMessage(confirm.error, hasAccount)}
            </p>
            <div className="flex justify-center gap-3">
              {/* Misafir sunucu tarafında engellendiyse tek işe yarar eylem giriş yapmaktır —
                  "Önizlemeye dön" onu aynı 401'e geri sokardı. */}
              {isGuestBlocked(confirm.error, hasAccount) ? (
                <Button asChild variant="cta">
                  <Link to="/login" state={{ from: '/reservation/new' }}>
                    Giriş yap
                  </Link>
                </Button>
              ) : (
                <Button
                  onClick={() => {
                    confirm.reset()
                    setWarning(null)
                  }}
                >
                  Önizlemeye dön
                </Button>
              )}
              <Button
                variant="ghost"
                className="text-muted-foreground hover:bg-muted hover:text-foreground"
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
  // Belirsiz sonuç (202) — satın alma gerçekleşmiş olabilir; kullanıcı listeyi kontrol etmeli.
  // `warning`DAN ÖNCE gelir: yukarıdaki effect uyarıyı temizliyor ama effect'ler boyamadan SONRA
  // çalışır, yani sıralama tersine olsaydı uyarı ekranı (ve "Yine de onayla" düğmesi) bir kare
  // boyunca görünürdü. Sonucu bilinmeyen bir booking'de ikinci onay riski alınmaz.
  if (confirm.data?.kind === 'pending') {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={3} />
        <Card className="glass-card border-border bg-card text-foreground">
          <CardContent className="space-y-4 p-8 text-center">
            <Clock className="mx-auto h-10 w-10 text-warning-foreground" aria-hidden />
            <h1 className="text-xl font-bold">Sonuç doğrulanıyor</h1>
            <p className="text-sm text-muted-foreground">
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

  if (warning) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={2} />
        <Card className="glass-card border-warning/30 bg-warning/10 text-foreground">
          <CardHeader className="flex-row items-center gap-3 space-y-0">
            <AlertTriangle className="h-6 w-6 shrink-0 text-warning-foreground" aria-hidden />
            <CardTitle>Onayınız gerekiyor</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Rezervasyon sağlayıcısı bu işlemle ilgili bir uyarı bildirdi. Devam etmek için lütfen
              onaylayın:
            </p>
            <ul className="list-disc space-y-1 pl-5 text-sm text-warning-foreground">
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
                    <Spinner size={16} decorative className="text-foreground" />
                    Gönderiliyor…
                  </>
                ) : (
                  'Yine de onayla'
                )}
              </Button>
              <Button
                variant="ghost"
                className="text-muted-foreground hover:bg-muted hover:text-foreground"
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

  if (!draft) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 py-12 text-center">
        <h1 className="text-2xl font-bold text-foreground">Rezervasyon</h1>
        <p className="text-sm text-muted-foreground">
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

  // 0. adım — hesabı olmayan kullanıcıya giriş/misafir seçimi. `!draft` kontrolünden SONRA gelir:
  // ürünü olmayan kullanıcıya önce "bir ürün seçin" demek, sonra nasıl devam edeceğini sormak
  // yerine doğru sıradır. Seçim yerel state'te tutulur — kalıcı olması gerekmez, çünkü ürün
  // taslağı da (Redux) kalıcı değil: sayfa yenilenirse ikisi birden sıfırlanır, tutarlı kalır.
  if (!hasAccount && !guestFlowAccepted) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <FormStepper current={1} />
        <h1 className="text-2xl font-bold text-foreground">Rezervasyon</h1>
        <GuestCheckoutChoice draft={draft} onGuestContinue={() => setGuestFlowAccepted(true)} />
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
        <h1 className="text-2xl font-bold text-foreground">Rezervasyon önizleme</h1>
        <Card className="glass-card border-border bg-card text-foreground">
          <CardHeader>
            <CardTitle>{draft.title}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">{draft.summary}</p>

            {/* Rezervasyon snapshot'ı — kullanıcı onaydan önce gerçek booking verisini görür. */}
            {draft.productType === 'hotel' ? (
              <dl className="grid gap-x-6 gap-y-2 rounded-lg border border-border bg-muted p-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Giriş / çıkış</dt>
                  <dd className="font-medium">
                    {formatDate(draft.hotel.checkIn)} — {formatDate(draft.hotel.checkOut)}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Oda / kişi</dt>
                  <dd className="font-medium">
                    {draft.hotel.rooms} oda · {draft.hotel.adults} yetişkin
                    {draft.hotel.children ? ` · ${draft.hotel.children} çocuk` : ''}
                  </dd>
                </div>
                {draft.hotel.boardType && (
                  <div>
                    <dt className="text-muted-foreground">Pansiyon</dt>
                    <dd className="font-medium">{draft.hotel.boardType}</dd>
                  </div>
                )}
              </dl>
            ) : (
              <dl className="grid gap-x-6 gap-y-2 rounded-lg border border-border bg-muted p-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Rota</dt>
                  <dd className="font-medium">
                    {draft.flight.origin} → {draft.flight.destination}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Kalkış</dt>
                  <dd className="font-medium">{formatDateTime(draft.flight.departTime)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Yolcu</dt>
                  <dd className="font-medium">{draft.flight.passengerCount}</dd>
                </div>
              </dl>
            )}

            <div>
              <p className="mb-1 text-sm font-semibold">Misafirler</p>
              <ul className="space-y-1 text-sm text-muted-foreground">
                {preview.data.passengerNames.map((name, i) => (
                  <li key={`${name}-${i}`}>{name}</li>
                ))}
              </ul>
            </div>
            {/* Fiyat aramadan bu yana oynadıysa (TourVisio canlı yeniden fiyatladı) farkı AÇIKÇA
                göster ve AYRI bir kabul iste — K21. Genel "bilgilerimi kontrol ettim" onayı bunun
                yerine geçmez: kullanıcı aramada gördüğü tutarı onayladığını sanır. */}
            {preview.data.priceChanged && (
              <div
                role="alert"
                className="space-y-2 rounded-lg border border-warning/40 bg-warning/10 p-3"
              >
                <p className="flex items-center gap-2 text-sm font-semibold">
                  <AlertTriangle className="h-4 w-4 shrink-0 text-warning-foreground" aria-hidden />
                  Fiyat güncellendi
                </p>
                <p className="text-sm text-muted-foreground">
                  Sağlayıcı bu ürünü yeniden fiyatladı. Rezervasyon YENİ tutarla oluşturulacak.
                </p>
                {preview.data.previousAmount != null && (
                  <p className="text-sm">
                    <span className="text-muted-foreground line-through">
                      {formatPrice(preview.data.previousAmount, preview.data.currency)}
                    </span>{' '}
                    <span aria-hidden>→</span>{' '}
                    <span className="font-bold">
                      {formatPrice(preview.data.totalAmount, preview.data.currency)}
                    </span>
                  </p>
                )}
                <label className="flex items-start gap-3 text-sm">
                  <input
                    type="checkbox"
                    checked={priceAccepted}
                    onChange={(e) => setPriceAccepted(e.target.checked)}
                    className="mt-0.5 h-5 w-5 rounded border-border accent-primary"
                  />
                  Yeni fiyatı kabul ediyorum.
                </label>
              </div>
            )}

            <p className="text-lg font-bold">
              Toplam: {formatPrice(preview.data.totalAmount, preview.data.currency)}
            </p>

            <label className="flex items-start gap-3 rounded-lg border border-primary/30 bg-primary/10 p-3 text-sm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
                className="mt-0.5 h-5 w-5 rounded border-border accent-primary"
              />
              Bilgilerimi kontrol ettim, rezervasyonu onaylıyorum.
            </label>

            <div className="flex gap-3">
              <Button
                variant="cta"
                disabled={!confirmed || (preview.data.priceChanged && !priceAccepted) || confirm.isPending}
                onClick={() => confirm.mutate({ previewId: preview.data!.previewId })}
              >
                {confirm.isPending ? (
                  <>
                    <Spinner size={16} decorative className="text-brand-navy" />
                    Gönderiliyor…
                  </>
                ) : (
                  'Rezervasyonu onayla'
                )}
              </Button>
              <Button
                variant="ghost"
                className="text-muted-foreground hover:bg-muted hover:text-foreground"
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
      <h1 className="text-2xl font-bold text-foreground">Rezervasyon</h1>
      <AiOffBanner />

      <Card className="glass-card border-border bg-card text-foreground">
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
          <h2 className="font-semibold text-foreground">Misafir / yolcu bilgileri</h2>
          {/* Yolcu sayısı ve tipi aramanızla eşleşir (TourVisio şartı); ünvan ve uyruk zorunludur. */}
          <p className="text-xs text-muted-foreground">
            Yolcu sayısı ve tipi aramanızla eşleşir. Ünvan ve uyruk zorunludur.
          </p>
          {fields.map((field, index) => {
            const pErr = errors.passengers?.[index]
            const isChild = field.passengerType === 'child'
            return (
              <div
                key={field.id}
                className="space-y-3 rounded-xl border border-border bg-muted p-4 text-foreground"
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
                    {/* Serbest metin yerine liste: 'ZZ' gibi iki harfli uydurmalar TourVisio'ya
                        gidemez ve ana misafirin uyruğu telefon kodunu besler. */}
                    <Controller
                      control={control}
                      name={`passengers.${index}.nationality`}
                      render={({ field: nationalityField }) => (
                        <CountrySelect
                          id={`passenger-${index}-nationality`}
                          value={nationalityField.value}
                          onChange={nationalityField.onChange}
                          onBlur={nationalityField.onBlur}
                          aria-invalid={!!pErr?.nationality}
                          aria-describedby={
                            pErr?.nationality ? `passenger-${index}-nationality-error` : undefined
                          }
                        />
                      )}
                    />
                    {pErr?.nationality && (
                      <p
                        id={`passenger-${index}-nationality-error`}
                        className="text-xs text-destructive-emphasis"
                      >
                        {pErr.nationality.message}
                      </p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-firstName`}>Ad</Label>
                    <Input
                      id={`passenger-${index}-firstName`}
                      autoComplete="given-name"
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
                        className="text-xs text-destructive-emphasis"
                      >
                        {pErr.firstName.message}
                      </p>
                    )}
                  </div>
                  <div className="grid gap-1.5">
                    <Label htmlFor={`passenger-${index}-lastName`}>Soyad</Label>
                    <Input
                      id={`passenger-${index}-lastName`}
                      autoComplete="family-name"
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
                        className="text-xs text-destructive-emphasis"
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
                      <p id={`passenger-${index}-age-error`} className="text-xs text-destructive-emphasis">
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
                          autoComplete="bday"
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
                            className="text-xs text-destructive-emphasis"
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
                            className="text-xs text-destructive-emphasis"
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
          <h2 className="font-semibold text-foreground">İletişim</h2>
          {/* İki alan da herkes için zaten zorunlu (Zod); misafirde kritik olan, rezervasyona
              sonradan ulaşmanın TEK yolunun bu iletişim bilgisi olması — bunu açıkça söyle. */}
          {!hasAccount && (
            <p className="text-xs text-muted-foreground">
              Misafir rezervasyonunda e-posta ve telefon zorunludur — PNR kodunuzu ve bilet özetinizi
              buraya göndeririz.
            </p>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="contact-email">E-posta</Label>
              <Input
                id="contact-email"
                type="email"
                autoComplete="email"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'contact-email-error' : undefined}
                className={darkFieldClass}
                {...register('email')}
              />
              {errors.email && (
                <p id="contact-email-error" className="text-xs text-destructive-emphasis">
                  {errors.email.message}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="contact-phone">Telefon</Label>
              {/* Ülke kodu + ulusal numara ayrı taşınır: TourVisio ContactPhone'u {ülke, alan,
                  numara} olarak ister, tek parça metinden tahmin etmek yanlış ülke kodu üretiyordu. */}
              <div className="flex gap-2">
                <Controller
                  control={control}
                  name="phoneCountry"
                  render={({ field: countryField }) => (
                    <CountrySelect
                      variant="dial"
                      className="w-[7.5rem] shrink-0"
                      value={countryField.value}
                      onChange={countryField.onChange}
                      onBlur={countryField.onBlur}
                      aria-label="Telefon ülke kodu"
                      aria-invalid={!!errors.phoneCountry}
                      placeholder="Kod"
                    />
                  )}
                />
                <Controller
                  control={control}
                  name="phoneNumber"
                  render={({ field: numberField }) => (
                    <Input
                      id="contact-phone"
                      type="tel"
                      inputMode="tel"
                      autoComplete="tel-national"
                      placeholder="Ülke kodu olmadan"
                      aria-invalid={!!errors.phoneNumber}
                      aria-describedby={
                        errors.phoneNumber || errors.phoneCountry ? 'contact-phone-error' : undefined
                      }
                      className={cn(darkFieldClass, 'flex-1')}
                      value={numberField.value}
                      onBlur={numberField.onBlur}
                      // Yazarken/yapıştırırken ulusal numaraya indirger: rakam dışını, baştaki 0'ı ve
                      // yapıştırılan ülke kodunu ('+90 555…') atar, ülkenin hane sınırına kırpar.
                      onChange={(e) =>
                        numberField.onChange(parseNationalNumber(e.target.value, phoneCountry))
                      }
                    />
                  )}
                />
              </div>
              {(errors.phoneNumber || errors.phoneCountry) && (
                <p id="contact-phone-error" className="text-xs text-destructive-emphasis">
                  {errors.phoneNumber?.message ?? errors.phoneCountry?.message}
                </p>
              )}
              {!errors.phoneNumber && !errors.phoneCountry && dialCodeOf(phoneCountry) && (
                <p className="text-xs text-muted-foreground">
                  Ülke kodu uyruğa göre seçilir; gerekirse değiştirebilirsiniz.
                </p>
              )}
            </div>
          </div>
        </section>

        {validationErrors.length > 0 && (
          <div
            role="alert"
            className="space-y-1 rounded-lg border border-warning/30 bg-warning/10 p-3 text-sm text-warning-foreground"
          >
            <p className="font-semibold text-warning-foreground">Devam etmeden önce şunları düzeltin:</p>
            <ul className="list-disc space-y-0.5 pl-5">
              {validationErrors.map((msg, i) => (
                <li key={`${msg}-${i}`}>{msg}</li>
              ))}
            </ul>
          </div>
        )}
        {preview.isError && (
          <div className="space-y-2">
            <p role="alert" className="text-sm text-destructive-emphasis">
              {previewErrorMessage(preview.error, hasAccount)}
            </p>
            {isGuestBlocked(preview.error, hasAccount) && (
              <Button asChild variant="cta" size="sm">
                <Link to="/login" state={{ from: '/reservation/new' }}>
                  Giriş yap
                </Link>
              </Button>
            )}
          </div>
        )}
        <Button type="submit" variant="cta" disabled={preview.isPending}>
          {preview.isPending ? (
            <>
              <Spinner size={16} decorative className="text-brand-navy" />
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
