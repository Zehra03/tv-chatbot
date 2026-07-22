import { Link } from 'react-router-dom'
import { LogIn, Mail, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { ReservationDraft } from '@/features/reservation/reservationDraftSlice'
import { formatPrice } from '@/utils/format'

/**
 * Rezervasyonun 0. adımı — hesabı OLMAYAN (misafir) kullanıcıya ödeme/rezervasyon adımına
 * geçerken sunulan iki yol: "Giriş Yaparak Devam Et" / "Üye Olmadan (Misafir) Devam Et".
 *
 * Neden kapıda değil de sayfada: eskiden router'daki RequireAccount misafiri sessizce /login'e
 * atıyordu, yani misafir rezervasyon yapamıyordu ve seçim şansı da yoktu. Seçim burada, ürün
 * özetinin YANINDA yapılır — kullanıcı neyi rezerve ettiğini görerek karar verir.
 *
 * Giriş yolu /login'e `from` ile gider; LoginPage.goToApp giriş/kayıt sonrası kullanıcıyı tam
 * buraya geri getirir ve taslak korunur (reservationDraftSlice `sessionStarted`'ı bilinçli
 * olarak sıfırlamıyor). Hesabı olan kullanıcı bu ekranı hiç görmez.
 */
export function GuestCheckoutChoice({
  draft,
  onGuestContinue,
}: {
  draft: ReservationDraft
  onGuestContinue: () => void
}) {
  return (
    <div className="space-y-6">
      <Card className="border-border bg-card text-foreground">
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

      <section aria-labelledby="checkout-choice-heading" className="space-y-4">
        <div className="space-y-1">
          <h2 id="checkout-choice-heading" className="text-lg font-semibold text-foreground">
            Nasıl devam etmek istersiniz?
          </h2>
          <p className="text-sm text-muted-foreground">
            Rezervasyonu tamamlamak için hesap açmanız zorunlu değil.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          {/* Önerilen yol — rezervasyon kimliğe bağlanır, sonradan görüntülenip iptal edilebilir. */}
          <div className="flex flex-col rounded-2xl border border-border bg-card p-5 shadow-soft">
            <LogIn className="h-6 w-6 text-primary" aria-hidden />
            <h3 className="mt-3 font-semibold text-foreground">Giriş Yaparak Devam Et</h3>
            <p className="mt-1 flex-1 text-sm text-muted-foreground">
              Rezervasyonunuz hesabınıza kaydedilir; “Rezervasyonlarım” sayfasından görüntüleyebilir,
              yazdırabilir ve iptal edebilirsiniz.
            </p>
            <Button asChild variant="cta" className="mt-4 w-full">
              <Link to="/login" state={{ from: '/reservation/new' }}>
                Giriş Yaparak Devam Et
              </Link>
            </Button>
          </div>

          <div className="flex flex-col rounded-2xl border border-border bg-card p-5 shadow-soft">
            <UserRound className="h-6 w-6 text-primary" aria-hidden />
            <h3 className="mt-3 font-semibold text-foreground">Üye Olmadan (Misafir) Devam Et</h3>
            <p className="mt-1 flex-1 text-sm text-muted-foreground">
              Hesap açmadan tamamlayın. Rezervasyon tamamlanınca PNR kodunuz ve bilet özetiniz
              ekranda gösterilir.
            </p>
            <Button variant="secondary" className="mt-4 w-full" onClick={onGuestContinue}>
              Üye Olmadan (Misafir) Devam Et
            </Button>
          </div>
        </div>

        {/* Dürüst uyarı: misafir rezervasyonu kimliğe bağlı değil, "Rezervasyonlarım"da çıkmaz —
            kullanıcı PNR'ı saklamalı. Bunu SEÇİMDEN ÖNCE söylemek, sonra sürpriz yapmaktan iyidir. */}
        <p className="flex items-start gap-2 rounded-lg border border-border bg-muted p-3 text-sm text-muted-foreground">
          <Mail className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span>
            Misafir rezervasyonları “Rezervasyonlarım” listesinde görünmez. Bu yüzden misafir
            akışında <strong className="font-semibold text-foreground">e-posta ve telefon
            zorunludur</strong> — PNR bilginizi bu adrese göndeririz.
          </span>
        </p>
      </section>
    </div>
  )
}

export default GuestCheckoutChoice
