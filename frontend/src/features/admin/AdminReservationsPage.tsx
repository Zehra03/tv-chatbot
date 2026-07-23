import { AdminPageHeader } from './AdminPage'
import { AdminReservationList } from './AdminReservationList'

/**
 * /admin/reservations — üye ve misafir tüm rezervasyonlar: PNR ile arama, duruma göre filtre,
 * sayfalama ve iptal. Liste/iptal mantığı AdminReservationList'te; bu sayfa yalnızca başlığı ve
 * kapsamı (filtresiz = tüm ürünler) belirler.
 */
export function AdminReservationsPage() {
  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="Rezervasyon Yönetimi"
        description="Üye ve misafir tüm rezervasyonlar. PNR ile arayın, duruma göre filtreleyin."
      />
      <AdminReservationList emptyMessage="Sistemde henüz rezervasyon yok." />
    </div>
  )
}
