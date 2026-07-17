import type { FlightFilters, HotelFilters } from './uiSlice'

/**
 * Aktif filtre çiplerinin (PPMO B21) veri tarafı — o an listeyi daraltan
 * filtreleri okunur etikete çevirir ve her biri için "bunu temizleyen" kısmi
 * güncellemeyi taşır. Saf fonksiyon: React'a bağlı değil, doğrudan test edilir;
 * sayfa `clear`'ı `*FiltersChanged` ile dispatch eder.
 *
 * Sıralama bilerek dışarıda bırakıldı: listeyi daraltmaz, yalnız düzenler —
 * "aktif filtreler" grubunda çıkması yanıltıcı olurdu (filtre çubuğundaki
 * sıralama seçicisi zaten kendi durumunu gösteriyor).
 */
export interface FilterChipSpec<F> {
  /** React key + kaldırma düğmesinin ayırt edici adı. */
  key: string
  label: string
  /** Bu filtreyi boşa çeken kısmi güncelleme. */
  clear: Partial<F>
}

/** Filtre çubuğundaki yıldız seçeneklerinin etiketleriyle birebir (3/4 → "ve üzeri", 5 → tam). */
const STAR_LABELS: Record<number, string> = {
  3: '3★ ve üzeri',
  4: '4★ ve üzeri',
  5: '5★',
}

export function hotelFilterChips(filters: HotelFilters): FilterChipSpec<HotelFilters>[] {
  const chips: FilterChipSpec<HotelFilters>[] = []
  if (filters.minStars) {
    chips.push({
      key: 'minStars',
      label: STAR_LABELS[filters.minStars] ?? `${filters.minStars}★ ve üzeri`,
      clear: { minStars: null },
    })
  }
  if (filters.boardType) {
    chips.push({ key: 'boardType', label: `Pansiyon: ${filters.boardType}`, clear: { boardType: null } })
  }
  // Para birimi yazılmaz: filtre çubuğundaki alan da ("En yüksek fiyat") birimsiz,
  // sonuç listesi tek para biriminde gelmek zorunda değil — uydurma birim basmayız.
  if (filters.maxPrice) {
    chips.push({ key: 'maxPrice', label: `En fazla ${filters.maxPrice}`, clear: { maxPrice: null } })
  }
  return chips
}

export function flightFilterChips(filters: FlightFilters): FilterChipSpec<FlightFilters>[] {
  const chips: FilterChipSpec<FlightFilters>[] = []
  if (filters.nonstopOnly) {
    chips.push({ key: 'nonstopOnly', label: 'Aktarmasız', clear: { nonstopOnly: false } })
  }
  if (filters.airline) {
    chips.push({ key: 'airline', label: `Havayolu: ${filters.airline}`, clear: { airline: null } })
  }
  if (filters.maxPrice) {
    chips.push({ key: 'maxPrice', label: `En fazla ${filters.maxPrice}`, clear: { maxPrice: null } })
  }
  return chips
}
