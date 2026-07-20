import { HotelCard } from '@/features/hotels/HotelCard'
import { FlightCard } from '@/features/flights/FlightCard'
import { ShowMoreButton } from '@/components/ShowMoreButton'
import { useProgressiveReveal } from '@/lib/useProgressiveReveal'
import type { PartialCriteria, ResultCard } from '@/types'

/**
 * Sonuç kartları listesi — sonuç panelinde (ChatPage'in sağ ekranı / mobil
 * drawer) HotelCard/FlightCard'ın KOMPAKT varyantıyla render edilir (madde 8).
 * İlk 5 kart gösterilir; gerisi "Daha fazla göster" ile açılır (madde 9).
 * Seç akışı kartların içindedir ve kontrollü rezervasyon formuna gider;
 * chatbot booking YAPMAZ (docs/frontend-architecture.md §9).
 *
 * `criteria` = sohbetin biriken arama kriteri; "Seç"in rezervasyon snapshot'ını (otelde
 * giriş/çıkış-oda/kişi, uçuşta yolcu) kurabilmesi için karta iletilir. Kart tipiyle kriter
 * niyeti (intent) uyuşmuyorsa o karta kriter geçilmez (otel "Seç" kapanır — güvenli varsayılan).
 */
export function ResultCards({
  cards,
  criteria,
}: {
  cards: ResultCard[]
  criteria?: PartialCriteria
}) {
  const { visible, hasMore, remaining, showMore } = useProgressiveReveal(cards, 5)
  const hotelCriteria = criteria?.intent === 'hotel' ? criteria.criteria : undefined
  const flightCriteria = criteria?.intent === 'flight' ? criteria.criteria : undefined

  // "En uygun" vurgusu: tüm liste içinde en düşük fiyatlı (otelde müsait olanlar
  // arasında) ürün — peach rozet alır. Tek sonuçta vurgulanmaz.
  const eligible = cards.filter((c) => c.productType !== 'hotel' || c.product.availability)
  const bestValueId =
    cards.length > 1 && eligible.length > 0
      ? eligible.reduce((best, c) => (c.product.price < best.product.price ? c : best)).product.id
      : undefined
  // grid-cols-1 (minmax(0,1fr)) ŞART: düz `grid` tek örtük sütunu içeriğe göre
  // (min-content) boyutlandırır; dar panelde kart, kabından ~18px taşar ve
  // overflow-x-hidden onu sağdan kırpar (fiyat/Seç kesilir). minmax(0,1fr)
  // sütunu kap genişliğine sabitler → kart daralır, orta sütun truncate ile sığar.
  return (
    <div className="grid grid-cols-1 gap-2">
      {visible.map((card) =>
        card.productType === 'hotel' ? (
          <HotelCard
            key={card.product.id}
            product={card.product}
            criteria={hotelCriteria}
            compact
            recommended={card.product.id === bestValueId}
          />
        ) : (
          <FlightCard
            key={card.product.id}
            product={card.product}
            criteria={flightCriteria}
            compact
            recommended={card.product.id === bestValueId}
          />
        ),
      )}
      {hasMore && <ShowMoreButton remaining={remaining} onClick={showMore} className="mt-1" />}
    </div>
  )
}
