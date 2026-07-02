import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import {
  hotelFiltersChanged,
  hotelFiltersReset,
  type HotelSort,
} from '@/features/ui/uiSlice'

/**
 * Otel listesi filtre çubuğu — durum uiSlice'ta yaşar (§5), sunucudan gelen
 * sonuçlara istemci tarafında uygulanır. Pansiyon seçenekleri o anki
 * sonuçlardan türetilir.
 */
export function HotelFilters({ boardTypes }: { boardTypes: string[] }) {
  const filters = useAppSelector((s) => s.ui.hotelFilters)
  const dispatch = useAppDispatch()

  return (
    <div className="flex flex-wrap items-center gap-3">
      <NativeSelect
        aria-label="Yıldız filtresi"
        value={filters.minStars ?? ''}
        onChange={(e) =>
          dispatch(hotelFiltersChanged({ minStars: e.target.value ? Number(e.target.value) : null }))
        }
      >
        <option value="">Yıldız: tümü</option>
        <option value="3">3★ ve üzeri</option>
        <option value="4">4★ ve üzeri</option>
        <option value="5">5★</option>
      </NativeSelect>

      <NativeSelect
        aria-label="Pansiyon filtresi"
        value={filters.boardType ?? ''}
        onChange={(e) => dispatch(hotelFiltersChanged({ boardType: e.target.value || null }))}
      >
        <option value="">Pansiyon: tümü</option>
        {boardTypes.map((bt) => (
          <option key={bt} value={bt}>
            {bt}
          </option>
        ))}
      </NativeSelect>

      <Input
        type="number"
        min={0}
        aria-label="En yüksek fiyat"
        placeholder="En yüksek fiyat"
        className="w-40"
        value={filters.maxPrice ?? ''}
        onChange={(e) =>
          dispatch(hotelFiltersChanged({ maxPrice: e.target.value ? Number(e.target.value) : null }))
        }
      />

      <NativeSelect
        aria-label="Sıralama"
        value={filters.sort ?? ''}
        onChange={(e) =>
          dispatch(hotelFiltersChanged({ sort: (e.target.value || null) as HotelSort | null }))
        }
      >
        <option value="">Sıralama: önerilen</option>
        <option value="price-asc">Fiyat (artan)</option>
        <option value="price-desc">Fiyat (azalan)</option>
        <option value="stars-desc">Yıldız (azalan)</option>
      </NativeSelect>

      <Button type="button" variant="ghost" size="sm" onClick={() => dispatch(hotelFiltersReset())}>
        Temizle
      </Button>
    </div>
  )
}
