import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { darkFieldClass } from '@/lib/field-styles'
import {
  flightFiltersChanged,
  flightFiltersReset,
  type FlightSort,
} from '@/features/ui/uiSlice'

/**
 * Uçuş listesi filtre çubuğu — durum uiSlice'ta yaşar (§5), sunucudan gelen
 * sonuçlara istemci tarafında uygulanır. Havayolu seçenekleri o anki
 * sonuçlardan türetilir. Seçimler DropdownSelect (animasyonlu listbox) ile.
 */
export function FlightFilters({ airlines }: { airlines: string[] }) {
  const filters = useAppSelector((s) => s.ui.flightFilters)
  const dispatch = useAppDispatch()

  return (
    <div className="glass-card relative z-30 flex flex-wrap items-center gap-3 p-4">
      <label className="flex items-center gap-2 text-sm text-foreground">
        <input
          type="checkbox"
          checked={filters.nonstopOnly}
          onChange={(e) => dispatch(flightFiltersChanged({ nonstopOnly: e.target.checked }))}
          className="h-4 w-4 rounded border-foreground/30 accent-brand-teal"
        />
        Yalnızca direkt
      </label>

      <DropdownSelect
        aria-label="Havayolu filtresi"
        value={filters.airline ?? ''}
        options={[
          { value: '', label: 'Havayolu: tümü' },
          ...airlines.map((a) => ({ value: a, label: a })),
        ]}
        onChange={(v) => dispatch(flightFiltersChanged({ airline: v || null }))}
      />

      <Input
        type="number"
        min={0}
        aria-label="En yüksek fiyat"
        placeholder="En yüksek fiyat"
        className={`w-40 ${darkFieldClass}`}
        value={filters.maxPrice ?? ''}
        onChange={(e) =>
          dispatch(flightFiltersChanged({ maxPrice: e.target.value ? Number(e.target.value) : null }))
        }
      />

      <DropdownSelect
        aria-label="Sıralama"
        value={filters.sort ?? ''}
        options={[
          { value: '', label: 'Sıralama: önerilen' },
          { value: 'price-asc', label: 'Fiyat (artan)' },
          { value: 'depart-asc', label: 'Kalkış saati' },
        ]}
        onChange={(v) => dispatch(flightFiltersChanged({ sort: (v || null) as FlightSort | null }))}
      />

      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="text-muted-foreground hover:bg-foreground/10 hover:text-foreground"
        onClick={() => dispatch(flightFiltersReset())}
      >
        Temizle
      </Button>
    </div>
  )
}
