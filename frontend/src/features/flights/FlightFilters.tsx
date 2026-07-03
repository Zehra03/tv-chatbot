import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import {
  flightFiltersChanged,
  flightFiltersReset,
  type FlightSort,
} from '@/features/ui/uiSlice'

/**
 * Uçuş listesi filtre çubuğu — durum uiSlice'ta yaşar (§5), sunucudan gelen
 * sonuçlara istemci tarafında uygulanır. Havayolu seçenekleri o anki
 * sonuçlardan türetilir.
 */
export function FlightFilters({ airlines }: { airlines: string[] }) {
  const filters = useAppSelector((s) => s.ui.flightFilters)
  const dispatch = useAppDispatch()

  return (
    <div className="flex flex-wrap items-center gap-3">
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={filters.nonstopOnly}
          onChange={(e) => dispatch(flightFiltersChanged({ nonstopOnly: e.target.checked }))}
          className="h-4 w-4 rounded border-input accent-primary"
        />
        Yalnızca direkt
      </label>

      <NativeSelect
        aria-label="Havayolu filtresi"
        value={filters.airline ?? ''}
        onChange={(e) => dispatch(flightFiltersChanged({ airline: e.target.value || null }))}
      >
        <option value="">Havayolu: tümü</option>
        {airlines.map((a) => (
          <option key={a} value={a}>
            {a}
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
          dispatch(flightFiltersChanged({ maxPrice: e.target.value ? Number(e.target.value) : null }))
        }
      />

      <NativeSelect
        aria-label="Sıralama"
        value={filters.sort ?? ''}
        onChange={(e) =>
          dispatch(flightFiltersChanged({ sort: (e.target.value || null) as FlightSort | null }))
        }
      >
        <option value="">Sıralama: önerilen</option>
        <option value="price-asc">Fiyat (artan)</option>
        <option value="depart-asc">Kalkış saati</option>
      </NativeSelect>

      <Button type="button" variant="ghost" size="sm" onClick={() => dispatch(flightFiltersReset())}>
        Temizle
      </Button>
    </div>
  )
}
