import { EmptyState } from '@/components/EmptyState'
import { ShowMoreButton } from '@/components/ShowMoreButton'
import { FlightCard } from '@/features/flights/FlightCard'
import { useProgressiveReveal } from '@/lib/useProgressiveReveal'
import type { FlightProduct, FlightSearchCriteria } from '@/types'

/**
 * Filtre uygulanmış uçuş sonuç listesi; boş listede açıklayıcı mesaj gösterir.
 * İlk 5 uçuş gösterilir, gerisi "Daha fazla göster" ile açılır (madde 9 —
 * ilk boyama yükünü azaltır). Filtre/arama değişince baştan 5'e döner.
 * `criteria` kartlara iletilir ki "Seç" rezervasyon snapshot'ının yolcu sayısını taşıyabilsin.
 */
export function FlightList({
  products,
  criteria,
}: {
  products: FlightProduct[]
  criteria?: Partial<FlightSearchCriteria>
}) {
  const { visible, hasMore, remaining, showMore } = useProgressiveReveal(products, 5)
  if (products.length === 0) {
    return <EmptyState tone="dark">Kriterlere uyan uçuş bulunamadı.</EmptyState>
  }
  return (
    <div className="space-y-3">
      <div className="grid gap-3">
        {visible.map((flight) => (
          <FlightCard key={flight.id} product={flight} criteria={criteria} />
        ))}
      </div>
      {hasMore && <ShowMoreButton remaining={remaining} onClick={showMore} />}
    </div>
  )
}
