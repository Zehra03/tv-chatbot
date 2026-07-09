import { HotelCard } from '@/features/hotels/HotelCard'
import { FlightCard } from '@/features/flights/FlightCard'
import type { ResultCard } from '@/types'

/**
 * Thread içi sonuç kartları — asistan mesajındaki `cards` alanını, sonuç
 * sayfalarıyla paylaşılan HotelCard/FlightCard bileşenleriyle render eder
 * (docs/frontend-architecture.md §8). Seç akışı kartların içindedir ve
 * kontrollü rezervasyon formuna gider; chatbot booking YAPMAZ (§9).
 *
 * `tilt={false}`: chatte arama sonrası kartlarda 3B eğim istemiyoruz — eğim
 * yalnızca sonuç sayfalarındaki (/hotels, /flights) kartlarda kalıyor.
 */
export function ResultCards({ cards }: { cards: ResultCard[] }) {
  return (
    <div className="grid gap-2">
      {cards.map((card) =>
        card.productType === 'hotel' ? (
          <HotelCard key={card.product.id} product={card.product} tilt={false} />
        ) : (
          <FlightCard key={card.product.id} product={card.product} tilt={false} />
        ),
      )}
    </div>
  )
}
