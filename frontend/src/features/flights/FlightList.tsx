import { FlightCard } from '@/features/flights/FlightCard'
import type { FlightProduct } from '@/types'

/** Filtre uygulanmış uçuş sonuç listesi; boş listede açıklayıcı mesaj gösterir. */
export function FlightList({ products }: { products: FlightProduct[] }) {
  if (products.length === 0) {
    return <p className="text-sm text-muted-foreground">Kriterlere uyan uçuş bulunamadı.</p>
  }
  return (
    <div className="grid gap-3">
      {products.map((flight) => (
        <FlightCard key={flight.id} product={flight} />
      ))}
    </div>
  )
}
