import { MapPin, Plane, Star } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { formatDateTime, formatPrice } from '@/utils/format'
import type { FlightProduct, HotelProduct, ResultCard } from '@/types'

/**
 * Thread içi sonuç kartları — asistan mesajındaki `cards` alanını render eder
 * (docs/frontend-architecture.md §8). Fiyat/uygunluk backend'den (mock'ta
 * fixture) geldiği gibi gösterilir. Seçim aksiyonu kontrollü rezervasyon
 * akışına yönlendirir; chatbot booking yapmaz.
 */

function HotelResultCard({ product }: { product: HotelProduct }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-4 p-4">
        <div className="min-w-0">
          <p className="truncate font-semibold">{product.hotelName}</p>
          <p className="mt-0.5 flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="h-3.5 w-3.5" />
            {product.region}
            <span className="ml-1 inline-flex items-center gap-0.5">
              <Star className="h-3.5 w-3.5 fill-primary text-primary" />
              {product.stars}
            </span>
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Badge variant="secondary">{product.boardType}</Badge>
            {!product.availability && <Badge variant="destructive">Müsait değil</Badge>}
          </div>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-lg font-bold">{formatPrice(product.price, product.currency)}</p>
        </div>
      </CardContent>
    </Card>
  )
}

function FlightResultCard({ product }: { product: FlightProduct }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-4 p-4">
        <div className="min-w-0">
          <p className="flex items-center gap-1.5 font-semibold">
            <Plane className="h-4 w-4 text-primary" />
            {product.airline}
            <span className="font-normal text-muted-foreground">
              {product.origin} → {product.destination}
            </span>
          </p>
          <p className="mt-0.5 text-sm text-muted-foreground">
            {formatDateTime(product.departTime)}
            {product.tripType === 'round_trip' && product.returnDepartTime && (
              <> · Dönüş: {formatDateTime(product.returnDepartTime)}</>
            )}
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Badge variant="secondary">
              {product.stops === 0 ? 'Direkt' : `${product.stops} aktarma`}
            </Badge>
            <Badge variant="secondary">Bagaj: {product.baggage}</Badge>
          </div>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-lg font-bold">{formatPrice(product.price, product.currency)}</p>
        </div>
      </CardContent>
    </Card>
  )
}

export function ResultCards({ cards }: { cards: ResultCard[] }) {
  return (
    <div className="grid gap-2">
      {cards.map((card) =>
        card.productType === 'hotel' ? (
          <HotelResultCard key={card.product.id} product={card.product} />
        ) : (
          <FlightResultCard key={card.product.id} product={card.product} />
        ),
      )}
    </div>
  )
}
