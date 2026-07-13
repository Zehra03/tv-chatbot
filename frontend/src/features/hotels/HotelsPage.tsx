import { useMemo, useState, type FormEvent } from 'react'
import { Hotel } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SearchHero } from '@/components/SearchHero'
import { Skeleton } from '@/components/ui/skeleton'
import { DateRangePicker } from '@/components/ui/date-range-picker'
import { PeoplePicker } from '@/components/ui/people-picker'
import { useAppSelector } from '@/app/hooks'
import { heroFieldClass } from '@/lib/field-styles'
import { cn } from '@/lib/utils'
import { useHotelSearch } from '@/features/hotels/useHotelSearch'
import { HotelFilters } from '@/features/hotels/HotelFilters'
import { HotelList } from '@/features/hotels/HotelList'
import { LocationAutocomplete } from '@/features/flights/LocationAutocomplete'
import { hotelApi } from '@/api'
import { MAX_PARTY_SIZE } from '@/types'
import type { HotelSearchCriteria } from '@/types'
import hotelHero from '@/assets/hotel/valeriia-bugaiova-_pPHgeHz1uk-unsplash.jpg'

/**
 * /hotels — filtrelenebilir otel sonuç ekranı (docs/frontend-architecture.md §3).
 * Form kriterleri belirler (chat'te birikenlerle ön-doldurulur), arama React
 * Query ile kritere key'li çalışır, uiSlice filtreleri sonuca istemci
 * tarafında uygulanır.
 */
export function HotelsPage() {
  const chatCriteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  const prefill = chatCriteria?.intent === 'hotel' ? chatCriteria.criteria : undefined

  const [destination, setDestination] = useState(prefill?.destination ?? '')
  const [checkIn, setCheckIn] = useState(prefill?.checkIn ?? '')
  const [checkOut, setCheckOut] = useState(prefill?.checkOut ?? '')
  const [adults, setAdults] = useState(prefill?.adults ?? 2)
  const [childCount, setChildCount] = useState(prefill?.children ?? 0)
  const [childAges, setChildAges] = useState<number[]>(prefill?.childAges ?? [])
  const [rooms, setRooms] = useState(prefill?.rooms ?? 1)
  const [criteria, setCriteria] = useState<HotelSearchCriteria | null>(null)

  // childAges uzunluğu her zaman childCount ile tutarlı tutulur (types/search.ts
  // invariantı); yeni eklenen çocuk için varsayılan yaş 7, popover'da değiştirilir.
  const changeChildCount = (next: number) => {
    setChildCount(next)
    setChildAges((ages) =>
      next > ages.length ? [...ages, ...Array<number>(next - ages.length).fill(7)] : ages.slice(0, next),
    )
  }

  // Her oda en az bir yetişkin gerektirir → oda sayısı yetişkin sayısını aşamaz.
  // Yetişkin azalınca oda sayısını da kısarak geçersiz kritere (ör. 1 yetişkin,
  // 4 oda) girmeyi önleriz; oda sayacının üst sınırı doğrudan yetişkine bağlanır.
  const maxRooms = adults
  const changeAdults = (next: number) => {
    setAdults(next)
    setRooms((r) => Math.min(r, next))
  }

  const query = useHotelSearch(criteria)
  const filters = useAppSelector((s) => s.ui.hotelFilters)

  const boardTypes = useMemo(
    () => [...new Set((query.data ?? []).map((h) => h.boardType))],
    [query.data],
  )

  const visible = useMemo(() => {
    let list = query.data ?? []
    const { minStars, boardType, maxPrice, sort } = filters
    if (minStars) list = list.filter((h) => h.stars >= minStars)
    if (boardType) list = list.filter((h) => h.boardType === boardType)
    if (maxPrice) list = list.filter((h) => h.price <= maxPrice)
    if (sort === 'price-asc') list = [...list].sort((a, b) => a.price - b.price)
    if (sort === 'price-desc') list = [...list].sort((a, b) => b.price - a.price)
    if (sort === 'stars-desc') list = [...list].sort((a, b) => b.stars - a.stars)
    return list
  }, [query.data, filters])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!destination.trim() || !checkIn || !checkOut) return
    setCriteria({
      destination: destination.trim(),
      checkIn,
      checkOut,
      adults,
      children: childCount,
      childAges,
      // Güvenlik ağı: sayaç zaten bağlı olsa da odayı yetişkinle sınırla,
      // backend'e "oda > yetişkin" gibi geçersiz bir kriter gitmesin.
      rooms: Math.min(rooms, adults),
      nationality: 'TR',
      currency: 'EUR',
    })
  }

  const guestSummary = `${adults} yetişkin${childCount ? `, ${childCount} çocuk` : ''}, ${rooms} oda`

  return (
    <div className="space-y-6">
      {/* Skyscanner tarzı hero: fotoğraf + örtü üzerinde arama formu.
          Alanlar beyaz (heroFieldClass), takvim/misafir popover'ları koyu cam. */}
      <SearchHero
        image={hotelHero}
        title="Oteller"
        subtitle="Bir sonraki konaklamanı bul — sonuçları yıldız, pansiyon ve fiyata göre daralt."
      >
        <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-2">
          <div className="flex-1 basis-52 sm:max-w-72 sm:flex-none sm:basis-auto">
            <LocationAutocomplete
              id="hotel-destination"
              label="Nereye"
              fetchSuggestions={(q) => hotelApi.locations(q)}
              queryKeyBase="hotel-locations"
              value={destination}
              onTextChange={setDestination}
              onSelect={(loc) => setDestination(loc.name)}
              placeholder="Şehir, bölge veya otel adı"
              required
              fieldClassName={heroFieldClass}
              className="sm:w-72"
            />
          </div>
          {/* Giriş/Çıkış alanına tıklayınca takvim açılır (ayrı buton yok). */}
          <DateRangePicker
            checkIn={checkIn}
            checkOut={checkOut}
            onChange={(ci, co) => {
              setCheckIn(ci)
              setCheckOut(co)
            }}
            checkInId="hotel-checkin"
            checkOutId="hotel-checkout"
            fieldClassName={heroFieldClass}
            required
          />
          {/* Skyscanner'ın "Misafir ve oda sayısı" alanı: sayaçlar + çocuk yaşları. */}
          <PeoplePicker
            id="hotel-guests"
            label="Misafir ve oda"
            summary={guestSummary}
            rows={[
              { key: 'adults', label: 'Yetişkin', hint: '18 yaş ve üzeri', value: adults, min: 1, max: MAX_PARTY_SIZE },
              { key: 'children', label: 'Çocuk', hint: '0–17 yaş', value: childCount, min: 0, max: 6 },
              { key: 'rooms', label: 'Oda', hint: 'Her odada en az bir yetişkin', value: rooms, min: 1, max: maxRooms },
            ]}
            onRowChange={(key, value) => {
              if (key === 'adults') changeAdults(value)
              else if (key === 'children') changeChildCount(value)
              else setRooms(value)
            }}
            fieldClassName={cn('w-56', heroFieldClass)}
          >
            {childCount > 0 && (
              <div className="mt-4 border-t border-white/10 pt-3">
                <p className="text-xs font-medium text-brand-ice/70">
                  Çocuk yaşları (fiyatlama için)
                </p>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  {childAges.map((age, i) => (
                    <label key={i} className="grid gap-1 text-xs text-brand-ice/70">
                      {i + 1}. çocuğun yaşı
                      <select
                        value={age}
                        onChange={(e) =>
                          setChildAges((ages) =>
                            ages.map((a, j) => (j === i ? Number(e.target.value) : a)),
                          )
                        }
                        className="h-9 rounded-md border border-white/15 bg-white/5 px-2 text-sm text-white [color-scheme:dark] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-brand-teal"
                      >
                        {Array.from({ length: 18 }, (_, y) => (
                          <option key={y} value={y}>
                            {y}
                          </option>
                        ))}
                      </select>
                    </label>
                  ))}
                </div>
              </div>
            )}
          </PeoplePicker>
          {/* Yükseklik hero alanlarıyla (h-12) eşit — items-end satırında üst/alt hizalı. */}
          <Button type="submit" className="h-12">
            Ara
          </Button>
        </form>
      </SearchHero>

      {!criteria && (
        <EmptyState tone="dark" title="Aramaya hazır" icon={<Hotel className="h-5 w-5" />}>
          Sonuçları görmek için arama kriterlerini girin.
        </EmptyState>
      )}

      {query.isFetching && (
        <div className="space-y-3">
          <LoadingState label="Aranıyor…" className="text-brand-ice/70" />
          {/* Dekoratif iskelet kartlar — duyuruyu üstteki role="status" yapar. */}
          <div aria-hidden="true" className="grid gap-3">
            <Skeleton className="h-32" />
            <Skeleton className="h-32" />
            <Skeleton className="h-32" />
          </div>
        </div>
      )}

      {query.isError && !query.isFetching && (
        <ErrorState message={query.error.message} onRetry={() => query.refetch()} />
      )}

      {query.data && (
        <>
          <HotelFilters boardTypes={boardTypes} />
          <p className="text-sm text-brand-ice/70">{visible.length} sonuç</p>
          <HotelList products={visible} />
        </>
      )}
    </div>
  )
}
