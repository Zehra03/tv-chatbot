import { EmptyState } from '@/components/EmptyState'
import { HotelCard } from '@/features/hotels/HotelCard'
import type { HotelProduct } from '@/types'

/** Filtre uygulanmış otel sonuç listesi; boş listede açıklayıcı mesaj gösterir. */
export function HotelList({ products }: { products: HotelProduct[] }) {
  if (products.length === 0) {
    return <EmptyState>Kriterlere uyan otel bulunamadı.</EmptyState>
  }
  return (
    <div className="grid gap-3">
      {products.map((hotel) => (
        <HotelCard key={hotel.id} product={hotel} />
      ))}
    </div>
  )
}
