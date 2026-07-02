import { Plane } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { formatDateTime, formatPrice } from '@/utils/format'
import type { FlightProduct } from '@/types'

/**
 * Uçuş sonuç kartı — hem /flights listesinde hem chat thread'inde kullanılır.
 * Fiyat backend'den geldiği gibi gösterilir. Seç, ürünü taslağa yazıp
 * kontrollü rezervasyon formuna yönlendirir.
 */
export function FlightCard({ product }: { product: FlightProduct }) {
  const select = useSelectProduct()
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
        <div className="flex shrink-0 flex-col items-end gap-2">
          <p className="text-lg font-bold">{formatPrice(product.price, product.currency)}</p>
          <Button
            size="sm"
            onClick={() =>
              select({
                productType: 'flight',
                productId: product.id,
                title: `${product.airline} ${product.origin} → ${product.destination}`,
                summary: `${formatDateTime(product.departTime)} · ${
                  product.stops === 0 ? 'Direkt' : `${product.stops} aktarma`
                } · Bagaj: ${product.baggage}`,
                price: product.price,
                currency: product.currency,
              })
            }
          >
            Seç
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
