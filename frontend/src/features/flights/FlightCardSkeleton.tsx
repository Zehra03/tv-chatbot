import { Skeleton } from '@/components/ui/skeleton'

/**
 * FlightCard'ın (tam varyant) birebir iskeleti — yükleme sırasında gerçek kartla
 * AYNI yerleşimi/yüksekliği kaplar → içerik gelince liste zıplamaz (CLS 0, §6/§11).
 * Havayolu satırı + rota + rozet/Seç kutuları FlightCard ile aynı ölçülerde. Salt
 * dekoratif: çağıran yer aria-hidden sarmalar; duyuruyu LoadingState (role="status") yapar.
 */
export function FlightCardSkeleton() {
  return (
    <div className="glass-card p-5">
      {/* Havayolu (sol) + fiyat (sağ). */}
      <div className="flex items-center justify-between gap-3">
        <Skeleton className="h-4 w-32 rounded border-0" />
        <Skeleton className="h-6 w-24 rounded border-0" />
      </div>
      {/* Rota satırı — iki kod + ortadaki çizgi. */}
      <div className="mt-4 flex items-center justify-between gap-3">
        <Skeleton className="h-7 w-16 rounded border-0" />
        <Skeleton className="h-1 flex-1 rounded border-0" />
        <Skeleton className="h-7 w-16 rounded border-0" />
      </div>
      {/* Rozetler (sol) + Seç (sağ). */}
      <div className="mt-4 flex items-center justify-between gap-3">
        <div className="flex gap-2">
          <Skeleton className="h-6 w-28 rounded-full border-0" />
          <Skeleton className="h-6 w-20 rounded-full border-0" />
        </div>
        <Skeleton className="h-8 w-16 rounded-full border-0" />
      </div>
    </div>
  )
}

export default FlightCardSkeleton
