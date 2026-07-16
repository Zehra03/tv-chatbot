import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Printer } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Logo } from '@/components/Logo'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { useReservation } from '@/features/reservation/useReservation'
import {
  RESERVATION_PRODUCT_TYPE_LABELS,
  RESERVATION_STATUS_LABELS,
} from '@/features/reservation/status'
import type { ReservationDetail, ReservationStatus } from '@/types'
import { formatDate, formatDateTime, formatPrice } from '@/utils/format'

/**
 * Durum rozetinin YAZDIRMA renkleri. `reservationStatusVariant` koyu cam yüzey için
 * ayarlı Badge varyantları döndürür; kâğıtta (beyaz zemin, dolgu basılmayabilir)
 * okunmaz. Burada renk kenarlık + yazıda taşınır, dolgu yalnız dekoratiftir —
 * tarayıcı arka planları basmasa da durum okunur kalır.
 */
const STATUS_PRINT_STYLES: Record<ReservationStatus, string> = {
  pending: 'border-amber-600/40 bg-amber-50 text-amber-800',
  confirmed: 'border-emerald-600/40 bg-emerald-50 text-emerald-800',
  cancelled: 'border-slate-400/50 bg-slate-100 text-slate-700',
  failed: 'border-red-600/40 bg-red-50 text-red-800',
}

/** Voucher'da tekrar eden etiket/değer satırı. */
function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="break-inside-avoid">
      <dt className="text-[11px] uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{children}</dd>
    </div>
  )
}

/** Voucher bölümü — başlık + ince marka çizgisi + içerik. */
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="break-inside-avoid">
      <h2 className="border-b border-slate-200 pb-1 text-sm font-bold uppercase tracking-wide text-brand-navy">
        {title}
      </h2>
      <div className="mt-3">{children}</div>
    </section>
  )
}

function HotelSection({ hotel }: { hotel: NonNullable<ReservationDetail['hotel']> }) {
  return (
    <Section title="Otel">
      <dl className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
        <Row label="Otel">
          {hotel.hotelName}
          {hotel.stars ? ` · ${hotel.stars}★` : ''}
        </Row>
        {hotel.region && <Row label="Bölge">{hotel.region}</Row>}
        {hotel.boardType && <Row label="Pansiyon">{hotel.boardType}</Row>}
        <Row label="Giriş / çıkış">
          {formatDate(hotel.checkIn)} — {formatDate(hotel.checkOut)}
        </Row>
        <Row label="Oda / kişi">
          {hotel.rooms} oda · {hotel.adults} yetişkin
          {hotel.children ? ` · ${hotel.children} çocuk` : ''}
        </Row>
      </dl>
    </Section>
  )
}

function FlightSection({ flight }: { flight: NonNullable<ReservationDetail['flight']> }) {
  return (
    <Section title="Uçuş">
      <dl className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
        <Row label="Rota">
          {flight.origin} → {flight.destination}
          {flight.airline ? ` · ${flight.airline}` : ''}
        </Row>
        <Row label="Kalkış">{formatDateTime(flight.departTime)}</Row>
        {flight.arriveTime && <Row label="Varış">{formatDateTime(flight.arriveTime)}</Row>}
        {flight.returnDepartTime && (
          <Row label="Dönüş">{formatDateTime(flight.returnDepartTime)}</Row>
        )}
        <Row label="Aktarma / bagaj">
          {flight.stops === 0 || flight.stops == null ? 'Direkt' : `${flight.stops} aktarma`}
          {flight.baggage ? ` · Bagaj: ${flight.baggage}` : ''}
        </Row>
        <Row label="Yolcu">{flight.passengerCount}</Row>
      </dl>
    </Section>
  )
}

/**
 * /reservations/:id/print — rezervasyon özetinin yazdırılabilir ("PDF olarak kaydet")
 * voucher'ı. Bilinçli olarak Layout'un DIŞINDA bir rotadır: kabuk `h-screen
 * overflow-hidden` ile viewport'a kilitlidir ve koyu gece-uçuşu yüzeyini boyar —
 * ikisi de kâğıda taşmayı/tek sayfaya sıkışmayı garanti eder. Chrome'suz açılan bu
 * sayfa `:root`'un açık token'larını alır, böylece voucher doğal olarak beyaz zeminde
 * koyu yazıdır.
 *
 * Veri detay sayfasıyla aynı ['reservations', id] önbelleğinden gelir; detaydan
 * gelindiğinde anında, listeden gelindiğinde tek istekle dolar. Hiçbir değer burada
 * üretilmez — yalnız backend'den geleni biçimlendirir.
 */
export function ReservationPrintPage() {
  const { id } = useParams()
  const { data, isError, isFetching, error, refetch } = useReservation(id)

  // Veri gelir gelmez yazdırma diyaloğunu aç — buton→route→diyalog tek akış olsun.
  // `!!data`ya bağlıyız (data nesnesine değil): iptal/tazeleme sonrası yeniden
  // basılmaz. Ref yerine cancelled bayrağı: StrictMode'un çift effect çağrısında
  // ref guard'ı ikinci turu tümden yutar ve geliştirmede diyalog hiç açılmaz.
  const ready = !!data
  useEffect(() => {
    if (!ready) return
    let cancelled = false
    // Diyalog boyamadan önce açılırsa yarım/boş sayfa basılır; fontlar geç
    // yüklenirse metin yeniden akar. İkisini de bekle (fonts jsdom'da yok).
    const fontsReady = document.fonts?.ready ?? Promise.resolve()
    void fontsReady.then(() => {
      requestAnimationFrame(() => {
        if (!cancelled) window.print()
      })
    })
    return () => {
      cancelled = true
    }
  }, [ready])

  return (
    <div className="min-h-screen bg-slate-100 py-8 print:bg-white print:py-0">
      {/* Ekran kabuğu — kâğıda basılmaz. */}
      <div className="print-hide mx-auto mb-6 flex max-w-[210mm] items-center justify-between gap-3 px-4">
        <Button asChild variant="ghost" size="sm" className="-ml-2">
          <Link to={`/reservations/${id}`}>
            <ArrowLeft className="h-4 w-4" />
            Rezervasyona dön
          </Link>
        </Button>
        {data && (
          <Button size="sm" onClick={() => window.print()}>
            <Printer className="h-4 w-4" />
            Yazdır / PDF olarak kaydet
          </Button>
        )}
      </div>

      {isFetching && !data && (
        <div className="print-hide mx-auto max-w-[210mm] px-4">
          <LoadingState label="Yükleniyor…" />
        </div>
      )}

      {isError && !isFetching && (
        <div className="print-hide mx-auto max-w-[210mm] px-4">
          <ErrorState message={error.message} onRetry={() => refetch()} />
        </div>
      )}

      {data && (
        // print-sheet: @media print'te gölge/kenarlık düşer, dolgular basılır (index.css).
        <article className="print-sheet mx-auto max-w-[210mm] bg-white p-10 text-slate-800 shadow-sm print:p-0 print:shadow-none">
          <header className="flex items-start justify-between gap-6 border-b-2 border-brand-navy pb-4">
            <div>
              <Logo height={44} />
              <p className="mt-2 text-lg font-bold text-brand-navy">Rezervasyon Özeti</p>
            </div>
            <div className="text-right">
              <p className="text-[11px] uppercase tracking-wide text-slate-500">
                Rezervasyon kodu
              </p>
              <p className="font-mono text-lg font-bold text-brand-navy">
                {data.reservationNumber}
              </p>
              <span
                className={`mt-2 inline-block rounded-full border px-3 py-0.5 text-xs font-semibold ${
                  STATUS_PRINT_STYLES[data.status]
                }`}
              >
                {RESERVATION_STATUS_LABELS[data.status]}
              </span>
            </div>
          </header>

          <div className="mt-6 space-y-6">
            <Section title="Rezervasyon bilgileri">
              <dl className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
                <Row label="Ürün tipi">{RESERVATION_PRODUCT_TYPE_LABELS[data.productType]}</Row>
                <Row label="Rezervasyon tarihi">{formatDate(data.reservationDate)}</Row>
                {data.leadGuestName && <Row label="Ana misafir">{data.leadGuestName}</Row>}
                {/* Sağlayıcı referansı satın alınana dek null — yoksa satırı hiç basma. */}
                {data.externalReservationNumber && (
                  <Row label="Sağlayıcı referansı">
                    <span className="font-mono">{data.externalReservationNumber}</span>
                  </Row>
                )}
                <Row label="Toplam tutar">
                  <span className="text-base font-bold text-brand-navy">
                    {formatPrice(data.totalAmount, data.currency)}
                  </span>
                </Row>
              </dl>
            </Section>

            {data.hotel && <HotelSection hotel={data.hotel} />}
            {data.flight && <FlightSection flight={data.flight} />}

            <Section title="Misafirler">
              {data.passengers && data.passengers.length > 0 ? (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-[11px] uppercase tracking-wide text-slate-500">
                      <th scope="col" className="pb-1 font-semibold">Ad Soyad</th>
                      <th scope="col" className="pb-1 font-semibold">Tip</th>
                      <th scope="col" className="pb-1 font-semibold">Yaş</th>
                      <th scope="col" className="pb-1 font-semibold">Uyruk</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.passengers.map((p, i) => (
                      <tr
                        key={`${p.firstName}-${p.lastName}-${i}`}
                        className="break-inside-avoid border-b border-slate-100 last:border-0"
                      >
                        <td className="py-1.5 font-medium text-slate-900">
                          {p.firstName} {p.lastName}
                        </td>
                        <td className="py-1.5">
                          {p.passengerType === 'adult' ? 'Yetişkin' : 'Çocuk'}
                        </td>
                        <td className="py-1.5">{p.age != null ? `${p.age}` : '—'}</td>
                        <td className="py-1.5">{p.nationality ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p className="text-sm text-slate-500">Misafir bilgisi bulunmuyor.</p>
              )}
            </Section>
          </div>

          <footer className="mt-8 border-t border-slate-200 pt-3 text-[11px] text-slate-500">
            <p>
              Bu belge {formatDateTime(new Date().toISOString())} tarihinde PaxAssist üzerinden
              oluşturulmuştur.
            </p>
            <p className="mt-0.5">
              Rezervasyonunuzun güncel durumu için PaxAssist → Rezervasyonlarım sayfasını ziyaret
              edebilirsiniz.
            </p>
          </footer>
        </article>
      )}
    </div>
  )
}
