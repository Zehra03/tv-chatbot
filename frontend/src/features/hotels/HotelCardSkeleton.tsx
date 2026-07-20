import { Skeleton } from '@/components/ui/skeleton'

/**
 * HotelCard'ın (tam varyant) birebir iskeleti — yükleme sırasında gerçek kartla
 * AYNI yerleşimi/yüksekliği kaplar → içerik gelince liste zıplamaz (CLS 0, §6/§11).
 * Görsel/metin/rozet/fiyat kutuları HotelCard ile aynı ölçülerde. Salt dekoratif:
 * çağıran yer aria-hidden sarmalar; duyuruyu LoadingState (role="status") yapar.
 */
export function HotelCardSkeleton() {
  return (
    <div className="glass-card flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
      <div className="flex min-w-0 flex-1 flex-col gap-3 sm:flex-row sm:items-center sm:gap-4">
        {/* Görsel — HotelCard ile aynı 4:3 kutu (mobilde geniş, sm+ 128×96). */}
        <Skeleton className="h-40 w-full shrink-0 rounded-xl border-0 sm:h-auto sm:w-32 sm:aspect-[4/3]" />
        <div className="min-w-0 flex-1 space-y-2">
          <Skeleton className="h-3 w-20 rounded border-0" /> {/* bölge */}
          <Skeleton className="h-5 w-44 max-w-full rounded-md border-0" /> {/* otel adı */}
          <Skeleton className="h-4 w-24 rounded border-0" /> {/* yıldız */}
          <div className="flex gap-2 pt-0.5">
            <Skeleton className="h-5 w-16 rounded-full border-0" />
            <Skeleton className="h-5 w-16 rounded-full border-0" />
          </div>
        </div>
      </div>
      {/* Fiyat + Seç sütunu — sm+ sağa yaslı. */}
      <div className="flex shrink-0 items-center justify-between gap-2 sm:flex-col sm:items-end">
        <div className="space-y-1.5 sm:flex sm:flex-col sm:items-end">
          <Skeleton className="h-6 w-24 rounded border-0" /> {/* fiyat */}
          <Skeleton className="h-3 w-28 rounded border-0" /> {/* gecelik */}
        </div>
        <Skeleton className="h-8 w-16 rounded-full border-0" /> {/* Seç */}
      </div>
    </div>
  )
}

export default HotelCardSkeleton
