import { HotelCard } from '@/features/hotels/HotelCard'
import { FlightCard } from '@/features/flights/FlightCard'
import type { ResultCard } from '@/types'

/**
 * Thread içi sonuç kartları — asistan mesajındaki `cards` alanını, sonuç
 * sayfalarıyla paylaşılan HotelCard/FlightCard bileşenleriyle render eder
 * (docs/frontend-architecture.md §8). Seç akışı kartların içindedir ve
 * kontrollü rezervasyon formuna gider; chatbot booking YAPMAZ (§9).
 */
export function ResultCards({ cards }: { cards: ResultCard[] }) {
  return (
    <div className="grid gap-2">
      {cards.map((card) =>
        card.productType === 'hotel' ? (
          <HotelCard key={card.product.id} product={card.product} />
        ) : (
          <FlightCard key={card.product.id} product={card.product} />
        ),
      )}
    </div>
  )
}
