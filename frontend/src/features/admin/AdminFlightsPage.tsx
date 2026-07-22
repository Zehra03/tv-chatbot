import { Info } from 'lucide-react'
import { AdminPageHeader } from './AdminPage'
import { AdminReservationList } from './AdminReservationList'

/**
 * /admin/flights — uçuş rezervasyonları.
 *
 * Bilinçli olarak bir "uçuş envanteri" ekranı DEĞİL: bu sistemde uçuş kataloğu tutulmuyor,
 * aranabilir uçuşlar her aramada TourVisio'dan canlı geliyor (backend `flight/` modülünde uçuş
 * entity'si yoktur). Elle uçuş eklemek, olmayan bir arzı varmış gibi göstermek olurdu — projenin
 * "fiyat/uygunluk uydurma" kuralının tam karşısında. Yönetilebilen gerçek veri, yapılmış uçuş
 * rezervasyonlarıdır; ekran onları listeler ve iptal ettirir. Aşağıdaki not bunu kullanıcıya da
 * söyler, ekranın eksik göründüğü sanılmasın.
 */
export function AdminFlightsPage() {
  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="Uçuş Yönetimi"
        description="Sistemdeki uçuş rezervasyonları — arama, filtreleme ve iptal."
      />

      <div className="flex items-start gap-3 rounded-xl border border-border bg-muted/40 p-4">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden />
        <p className="text-sm text-muted-foreground">
          Uçuş arzı PaxAssist'te tutulmaz; uçuşlar her aramada TourVisio'dan canlı gelir. Bu
          nedenle burada uçuş <em>eklenmez</em> — yönetilen veri, yapılmış uçuş rezervasyonlarıdır.
        </p>
      </div>

      <AdminReservationList
        productType="flight"
        emptyMessage="Henüz uçuş rezervasyonu yok."
      />
    </div>
  )
}
