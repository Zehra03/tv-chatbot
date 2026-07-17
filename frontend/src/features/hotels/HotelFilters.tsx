import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { darkFieldClass } from '@/lib/field-styles'
import {
  hotelFiltersChanged,
  hotelFiltersReset,
  type HotelSort,
} from '@/features/ui/uiSlice'

/**
 * Otel listesi filtre çubuğu — durum uiSlice'ta yaşar (§5), sunucudan gelen
 * sonuçlara istemci tarafında uygulanır. Pansiyon seçenekleri o anki
 * sonuçlardan türetilir. Seçimler DropdownSelect (animasyonlu listbox) ile.
 */
export function HotelFilters({ boardTypes }: { boardTypes: string[] }) {
  const filters = useAppSelector((s) => s.ui.hotelFilters)
  const dispatch = useAppDispatch()

  return (
    <div className="glass-card relative z-30 flex flex-wrap items-center gap-3 p-4">
      <DropdownSelect
        aria-label="Yıldız filtresi"
        value={filters.minStars ? String(filters.minStars) : ''}
        options={[
          { value: '', label: 'Yıldız: tümü' },
          { value: '3', label: '3★ ve üzeri' },
          { value: '4', label: '4★ ve üzeri' },
          { value: '5', label: '5★' },
        ]}
        onChange={(v) => dispatch(hotelFiltersChanged({ minStars: v ? Number(v) : null }))}
      />

      <DropdownSelect
        aria-label="Pansiyon filtresi"
        value={filters.boardType ?? ''}
        options={[
          { value: '', label: 'Pansiyon: tümü' },
          ...boardTypes.map((bt) => ({ value: bt, label: bt })),
        ]}
        onChange={(v) => dispatch(hotelFiltersChanged({ boardType: v || null }))}
      />

      <Input
        type="number"
        min={0}
        aria-label="En yüksek fiyat"
        placeholder="En yüksek fiyat"
        className={`w-40 ${darkFieldClass}`}
        value={filters.maxPrice ?? ''}
        onChange={(e) =>
          dispatch(hotelFiltersChanged({ maxPrice: e.target.value ? Number(e.target.value) : null }))
        }
      />

      <DropdownSelect
        aria-label="Sıralama"
        value={filters.sort ?? ''}
        options={[
          { value: '', label: 'Sıralama: önerilen' },
          { value: 'price-asc', label: 'Fiyat (artan)' },
          { value: 'price-desc', label: 'Fiyat (azalan)' },
          { value: 'stars-desc', label: 'Yıldız (azalan)' },
        ]}
        onChange={(v) => dispatch(hotelFiltersChanged({ sort: (v || null) as HotelSort | null }))}
      />

      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="text-muted-foreground hover:bg-foreground/10 hover:text-foreground"
        onClick={() => dispatch(hotelFiltersReset())}
      >
        Temizle
      </Button>
    </div>
  )
}
