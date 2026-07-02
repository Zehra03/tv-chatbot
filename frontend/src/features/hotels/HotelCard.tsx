import { MapPin, Star } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { formatPrice } from '@/utils/format'
import type { HotelProduct } from '@/types'

/**
 * Otel sonuç kartı — hem /hotels listesinde hem chat thread'inde kullanılır.
 * Fiyat/uygunluk backend'den geldiği gibi gösterilir. Seç, ürünü taslağa yazıp
 * kontrollü rezervasyon formuna yönlendirir; müsait değilse devre dışıdır.
 */
export function HotelCard({ product }: { product: HotelProduct }) {
  const select = useSelectProduct()
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-4 p-4">
        <div className="min-w-0">
          <p className="truncate font-semibold">{product.hotelName}</p>
          <p className="mt-0.5 flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="h-3.5 w-3.5" />
            {product.region}
            <span className="ml-1 inline-flex items-center gap-0.5">
              <Star className="h-3.5 w-3.5 fill-primary text-primary" aria-hidden />
              {product.stars}
              <span className="sr-only"> yıldız</span>
            </span>
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Badge variant="secondary">{product.boardType}</Badge>
            {!product.availability && <Badge variant="destructive">Müsait değil</Badge>}
          </div>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          <p className="text-lg font-bold">{formatPrice(product.price, product.currency)}</p>
          <Button
            size="sm"
            disabled={!product.availability}
            aria-label={`${product.hotelName} otelini seç`}
            onClick={() =>
              select({
                productType: 'hotel',
                productId: product.id,
                title: product.hotelName,
                summary: `${product.region} · ${product.stars}★ · ${product.boardType}`,
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
