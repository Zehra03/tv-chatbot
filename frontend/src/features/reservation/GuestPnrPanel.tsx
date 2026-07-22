import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, CheckCircle2, Copy, Mail } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import type { PreviewReservationCommand } from '@/api'
import type { ReservationSummary } from '@/types'
import { formatDate, formatDateTime, formatPrice } from '@/utils/format'

/**
 * Misafir rezervasyonunun SONUÇ ekranı (3. adım) — PNR kodu + bilet özeti + "PNR e-postanıza
 * gönderildi" bilgisi.
 *
 * Hesabı olan kullanıcı bunun yerine kalıcı /reservations/:id sayfasına yönlendirilir; misafir
 * ORAYA GİREMEZ (RequireAccount + backend `/api/v1/reservations/**` yalnız USER/ADMIN'e açık),
 * bu yüzden sonucu satır içi ve TEK SEFERLİK gösteririz. Kullanıcının elinde kalacak tek şey PNR
 * kodu olduğundan kopyalama düğmesi ve "saklayın" uyarısı bilinçli olarak öne çıkarılmıştır.
 *
 * Özet, onaydan önce dondurulan komuttan (`command`) okunur — taslak (`reservationDraft`) başarıda
 * temizleniyor, yani bu noktada artık yok. Tutar/kodlar ise backend'in döndürdüğü `reservation`
 * kaydından gelir: ekranda uydurulmuş fiyat/kod gösterilmez (CLAUDE.md).
 */
export function GuestPnrPanel({
  reservation,
  command,
}: {
  reservation: ReservationSummary
  command: PreviewReservationCommand
}) {
  const [copied, setCopied] = useState(false)
  const lead = command.travellers[0]
  const email = lead?.address?.email?.trim() || lead?.email?.trim() || ''
  const pnr = reservation.reservationNumber

  const copyPnr = async () => {
    try {
      await navigator.clipboard?.writeText(pnr)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      /* Pano izni yoksa sessizce geç — kod zaten ekranda seçilebilir hâlde duruyor. */
    }
  }

  return (
    <Card className="border-border bg-card text-foreground">
      <CardContent className="space-y-6 p-6 sm:p-8">
        <div className="space-y-2 text-center">
          <CheckCircle2 className="mx-auto h-10 w-10 text-success" aria-hidden />
          <h1 className="text-xl font-bold">Rezervasyonunuz tamamlandı</h1>
          <p className="text-sm text-muted-foreground">
            Aşağıdaki PNR kodunu saklayın — misafir rezervasyonunu bu kodla sorgulayabilirsiniz.
          </p>
        </div>

        {/* PNR — ekranın en belirgin öğesi. */}
        <div className="rounded-2xl border border-primary/30 bg-primary/10 p-5 text-center">
          <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
            PNR kodu
          </p>
          <p className="mt-2 select-all break-all font-mono text-2xl font-bold text-foreground">
            {pnr}
          </p>
          {reservation.externalReservationNumber && (
            <p className="mt-1 text-xs text-muted-foreground">
              Sağlayıcı referansı:{' '}
              <span className="select-all font-mono">{reservation.externalReservationNumber}</span>
            </p>
          )}
          <Button variant="secondary" size="sm" className="mt-4" onClick={() => void copyPnr()}>
            {copied ? (
              <>
                <Check className="h-4 w-4" aria-hidden />
                Kopyalandı
              </>
            ) : (
              <>
                <Copy className="h-4 w-4" aria-hidden />
                PNR kodunu kopyala
              </>
            )}
          </Button>
        </div>

        {/* Kabul kriteri: "PNR bilgisi e-posta adresinize gönderilmiştir" belirgin olmalı. */}
        <p
          role="status"
          className="flex items-start gap-3 rounded-lg border border-success/30 bg-success/10 p-3 text-sm"
        >
          <Mail className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden />
          <span>
            PNR bilgisi{' '}
            {email ? <strong className="font-semibold break-all">{email}</strong> : 'e-posta'}{' '}
            adresinize gönderilmiştir.
          </span>
        </p>

        {/* Bilet özeti */}
        <div className="space-y-3">
          <h2 className="font-semibold">Bilet özeti</h2>
          {command.hotel ? (
            <dl className="grid gap-x-6 gap-y-2 rounded-lg border border-border bg-muted p-3 text-sm sm:grid-cols-2">
              <div className="sm:col-span-2">
                <dt className="text-muted-foreground">Otel</dt>
                <dd className="font-medium">{command.hotel.hotelName}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Giriş / çıkış</dt>
                <dd className="font-medium">
                  {formatDate(command.hotel.checkIn)} — {formatDate(command.hotel.checkOut)}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Oda / kişi</dt>
                <dd className="font-medium">
                  {command.hotel.rooms} oda · {command.hotel.adults} yetişkin
                  {command.hotel.children ? ` · ${command.hotel.children} çocuk` : ''}
                </dd>
              </div>
              {command.hotel.boardType && (
                <div>
                  <dt className="text-muted-foreground">Pansiyon</dt>
                  <dd className="font-medium">{command.hotel.boardType}</dd>
                </div>
              )}
            </dl>
          ) : command.flight ? (
            <dl className="grid gap-x-6 gap-y-2 rounded-lg border border-border bg-muted p-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Rota</dt>
                <dd className="font-medium">
                  {command.flight.origin} → {command.flight.destination}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Kalkış</dt>
                <dd className="font-medium">{formatDateTime(command.flight.departTime)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Yolcu</dt>
                <dd className="font-medium">{command.flight.passengerCount}</dd>
              </div>
            </dl>
          ) : null}

          <div>
            <p className="mb-1 text-sm font-semibold">Yolcular</p>
            <ul className="space-y-1 text-sm text-muted-foreground">
              {command.travellers.map((t, i) => (
                <li key={`${t.firstName}-${t.lastName}-${i}`}>
                  {t.firstName} {t.lastName}
                </li>
              ))}
            </ul>
          </div>

          <p className="text-lg font-bold">
            Toplam: {formatPrice(reservation.totalAmount, reservation.currency)}
          </p>
        </div>

        <div className="flex flex-wrap gap-3 border-t border-border pt-4">
          <Button asChild variant="cta">
            <Link to="/chat">Yeni arama yap</Link>
          </Button>
          {/* Yükseltme yolu: hesap açan kullanıcı sonraki rezervasyonlarını listede takip edebilir. */}
          <Button asChild variant="secondary">
            <Link to="/login">Hesap oluştur</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

export default GuestPnrPanel
