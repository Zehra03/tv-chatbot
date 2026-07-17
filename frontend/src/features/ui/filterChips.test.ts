import { describe, expect, it } from 'vitest'
import { flightFilterChips, hotelFilterChips } from '@/features/ui/filterChips'
import uiReducer, {
  flightFiltersChanged,
  hotelFiltersChanged,
  type FlightFilters,
  type HotelFilters,
} from '@/features/ui/uiSlice'

const emptyHotel: HotelFilters = { minStars: null, boardType: null, maxPrice: null, sort: null }
const emptyFlight: FlightFilters = {
  nonstopOnly: false,
  airline: null,
  maxPrice: null,
  sort: null,
}

describe('hotelFilterChips', () => {
  it('filtre yokken çip üretmez', () => {
    expect(hotelFilterChips(emptyHotel)).toEqual([])
  })

  it('aktif filtreleri filtre çubuğundaki etiketlerle adlandırır', () => {
    const chips = hotelFilterChips({
      minStars: 4,
      boardType: 'AI',
      maxPrice: 1500,
      sort: 'price-asc',
    })
    expect(chips.map((c) => c.label)).toEqual(['4★ ve üzeri', 'Pansiyon: AI', 'En fazla 1500'])
    // 5★ "ve üzeri" değil — dropdown'daki seçenekle birebir.
    expect(hotelFilterChips({ ...emptyHotel, minStars: 5 })[0].label).toBe('5★')
  })

  it('sıralamayı çipe dökmez — listeyi daraltmaz, yalnız düzenler', () => {
    expect(hotelFilterChips({ ...emptyHotel, sort: 'stars-desc' })).toEqual([])
  })

  it('çipin clear\'ı dispatch edilince yalnız kendi filtresini boşaltır', () => {
    const filters: HotelFilters = { minStars: 4, boardType: 'AI', maxPrice: 1500, sort: 'price-asc' }
    const starChip = hotelFilterChips(filters).find((c) => c.key === 'minStars')!

    const next = uiReducer(
      { hotelFilters: filters, flightFilters: emptyFlight, activeModal: null, toast: null },
      hotelFiltersChanged(starChip.clear),
    )
    expect(next.hotelFilters).toEqual({
      minStars: null,
      boardType: 'AI',
      maxPrice: 1500,
      sort: 'price-asc',
    })
  })
})

describe('flightFilterChips', () => {
  it('filtre yokken çip üretmez', () => {
    expect(flightFilterChips(emptyFlight)).toEqual([])
  })

  it('aktif filtreleri adlandırır', () => {
    const chips = flightFilterChips({
      nonstopOnly: true,
      airline: 'MockAir',
      maxPrice: 300,
      sort: 'depart-asc',
    })
    expect(chips.map((c) => c.label)).toEqual(['Aktarmasız', 'Havayolu: MockAir', 'En fazla 300'])
  })

  it('aktarmasız çipi kaldırılınca filtre false\'a döner (null değil — boolean alan)', () => {
    const chip = flightFilterChips({ ...emptyFlight, nonstopOnly: true })[0]
    const next = uiReducer(
      {
        hotelFilters: emptyHotel,
        flightFilters: { ...emptyFlight, nonstopOnly: true },
        activeModal: null,
        toast: null,
      },
      flightFiltersChanged(chip.clear),
    )
    expect(next.flightFilters.nonstopOnly).toBe(false)
  })
})
