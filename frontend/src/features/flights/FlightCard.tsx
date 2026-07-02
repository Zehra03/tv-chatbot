import { Plane } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { formatDateTime } from '@/utils/format'
import type { FlightProduct } from '@/types'

/**
 * Uçuş sonuç kartı — hem /flights listesinde hem chat thread'inde kullanılır.
 * Görsel dil login vitrinindeki uçuş kartından: cam yüzey, noktalı rota
 * çizgisi, hover'da teal kenar + yükselme. Fiyat backend'den geldiği gibi
 * gösterilir (AnimatedPrice sr-only ikizinde formatPrice). Seç, ürünü taslağa
 * yazıp kontrollü rezervasyon formuna yönlendirir.
 */
export function FlightCard({ product }: { product: FlightProduct }) {
  const select = useSelectProduct()
  return (
    <div className="glass-card p-5 transition-all duration-300 hover:-translate-y-0.5 hover:border-brand-teal/60 hover:shadow-[0_8px_30px_theme(colors.brand.teal/15%)]">
      <div className="flex items-center justify-between gap-3">
        <p className="flex min-w-0 items-center gap-2 text-xs font-medium text-white/70">
          <Plane className="h-4 w-4 shrink-0 text-brand-teal" aria-hidden />
          <span className="truncate font-semibold text-white">{product.airline}</span>
        </p>
        <AnimatedPrice
          amount={product.price}
          currency={product.currency}
          className="shrink-0 text-xl font-bold text-white"
        />
      </div>

      {/* Rota çizgisi — login vitrin kartındaki desen. */}
      <div className="mt-4 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-2xl font-bold text-white">{product.origin}</p>
          <p className="mt-0.5 text-xs text-white/70">{formatDateTime(product.departTime)}</p>
        </div>
        <div className="flex min-w-16 flex-1 flex-col items-center px-2">
          <div className="flex w-full items-center gap-1">
            <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
            <span className="h-px flex-1 bg-white/40" />
            <Plane className="h-3.5 w-3.5 shrink-0 text-white/70" aria-hidden />
            <span className="h-px flex-1 bg-white/40" />
            <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
          </div>
          <p className="mt-1 text-[11px] text-white/60">
            {product.stops === 0 ? 'Direkt' : `${product.stops} aktarma`}
          </p>
        </div>
        <div className="min-w-0 text-right">
          <p className="truncate text-2xl font-bold text-white">{product.destination}</p>
          {product.tripType === 'round_trip' && product.returnDepartTime && (
            <p className="mt-0.5 text-xs text-white/70">
              Dönüş: {formatDateTime(product.returnDepartTime)}
            </p>
          )}
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          <Badge variant="glass">Bagaj: {product.baggage}</Badge>
        </div>
        <Button
          size="sm"
          className="rounded-full bg-gradient-to-r from-brand-blue to-brand-teal px-5 text-white shadow-[0_2px_12px_theme(colors.brand.teal/30%)] transition-shadow hover:shadow-[0_2px_20px_theme(colors.brand.teal/50%)]"
          aria-label={`${product.airline} ${product.origin} ${product.destination} uçuşunu seç`}
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
    </div>
  )
}
