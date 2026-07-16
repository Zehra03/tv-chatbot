import { EmptyState } from '@/components/EmptyState'
import { ShowMoreButton } from '@/components/ShowMoreButton'
import { HotelCard } from '@/features/hotels/HotelCard'
import { useProgressiveReveal } from '@/lib/useProgressiveReveal'
import type { HotelProduct, HotelSearchCriteria } from '@/types'

/**
 * Filtre uygulanmış otel sonuç listesi; boş listede açıklayıcı mesaj gösterir.
 * İlk 5 otel gösterilir, gerisi "Daha fazla göster" ile açılır (madde 9 —
 * ilk boyama yükünü azaltır). Filtre/arama değişince baştan 5'e döner.
 * `criteria` kartlara iletilir ki "Seç" rezervasyon snapshot'ını (giriş/çıkış, oda/kişi) kurabilsin.
 */
export function HotelList({
  products,
  criteria,
}: {
  products: HotelProduct[]
  criteria?: Partial<HotelSearchCriteria>
}) {
  const { visible, hasMore, remaining, showMore } = useProgressiveReveal(products, 5)
  if (products.length === 0) {
    return <EmptyState tone="dark">Kriterlere uyan otel bulunamadı.</EmptyState>
  }
  return (
    <div className="space-y-3">
      <div className="grid gap-3">
        {visible.map((hotel) => (
          <HotelCard key={hotel.id} product={hotel} criteria={criteria} />
        ))}
      </div>
      {hasMore && <ShowMoreButton remaining={remaining} onClick={showMore} />}
    </div>
  )
}
