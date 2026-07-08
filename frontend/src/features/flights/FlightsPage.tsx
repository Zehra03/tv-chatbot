import { useMemo, useState, type FormEvent } from 'react'
import { ArrowRightLeft, Plane } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { DatePicker } from '@/components/ui/date-picker'
import { DateRangePicker } from '@/components/ui/date-range-picker'
import { PeoplePicker } from '@/components/ui/people-picker'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SearchHero } from '@/components/SearchHero'
import { Skeleton } from '@/components/ui/skeleton'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { heroFieldClass } from '@/lib/field-styles'
import { cn } from '@/lib/utils'
import { flightFiltersChanged } from '@/features/ui/uiSlice'
import { useFlightSearch } from '@/features/flights/useFlightSearch'
import { FlightFilters } from '@/features/flights/FlightFilters'
import { FlightList } from '@/features/flights/FlightList'
import type { FlightSearchCriteria, TripType } from '@/types'
import flightHero from '@/assets/flight/philip-myrtorp-iiqpxCg2GD4-unsplash.jpg'

/**
 * /flights — filtrelenebilir uçuş sonuç ekranı (docs/frontend-architecture.md §3).
 * Form kriterleri belirler (chat'te birikenlerle ön-doldurulur), arama React
 * Query ile kritere key'li çalışır, uiSlice filtreleri sonuca istemci
 * tarafında uygulanır.
 */
export function FlightsPage() {
  const chatCriteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  const prefill = chatCriteria?.intent === 'flight' ? chatCriteria.criteria : undefined
  const dispatch = useAppDispatch()

  const [origin, setOrigin] = useState(prefill?.origin ?? '')
  const [destination, setDestination] = useState(prefill?.destination ?? '')
  const [departDate, setDepartDate] = useState(prefill?.departDate ?? '')
  const [returnDate, setReturnDate] = useState(prefill?.returnDate ?? '')
  const [passengers, setPassengers] = useState(prefill?.passengers ?? 1)
  const [tripType, setTripType] = useState<TripType>(prefill?.tripType ?? 'one_way')
  const [criteria, setCriteria] = useState<FlightSearchCriteria | null>(null)

  const query = useFlightSearch(criteria)
  const filters = useAppSelector((s) => s.ui.flightFilters)

  const airlines = useMemo(
    () => [...new Set((query.data ?? []).map((f) => f.airline))],
    [query.data],
  )

  const visible = useMemo(() => {
    let list = query.data ?? []
    const { nonstopOnly, airline, maxPrice, sort } = filters
    if (nonstopOnly) list = list.filter((f) => f.stops === 0)
    if (airline) list = list.filter((f) => f.airline === airline)
    if (maxPrice) list = list.filter((f) => f.price <= maxPrice)
    if (sort === 'price-asc') list = [...list].sort((a, b) => a.price - b.price)
    if (sort === 'depart-asc')
      list = [...list].sort((a, b) => a.departTime.localeCompare(b.departTime))
    return list
  }, [query.data, filters])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!origin.trim() || !destination.trim() || !departDate) return
    setCriteria({
      origin: origin.trim(),
      destination: destination.trim(),
      departDate,
      passengers,
      currency: 'EUR',
      tripType,
      ...(tripType === 'round_trip' && returnDate ? { returnDate } : {}),
    })
  }

  const swapPlaces = () => {
    setOrigin(destination)
    setDestination(origin)
  }

  return (
    <div className="space-y-6">
      {/* Skyscanner tarzı hero: fotoğraf + örtü üzerinde arama formu.
          Alanlar beyaz (heroFieldClass), takvim/yolcu popover'ları koyu cam. */}
      <SearchHero
        image={flightHero}
        title="Uçuşlar"
        subtitle="Milyonlarca rota, tek basit arama — sonuçları aktarma, havayolu ve fiyata göre daralt."
      >
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Skyscanner'daki gibi yön seçimi ve direkt uçuş tercihi alanların üstünde. */}
          <div className="flex flex-wrap items-center gap-4">
            <DropdownSelect
              id="flight-triptype"
              aria-label="Yön"
              value={tripType}
              options={[
                { value: 'one_way', label: 'Tek yön' },
                { value: 'round_trip', label: 'Gidiş-dönüş' },
              ]}
              onChange={(v) => setTripType(v as TripType)}
            />
            {/* Sonuç filtre çubuğuyla aynı uiSlice alanına yazar — ikisi senkron kalır. */}
            <label className="flex items-center gap-2 text-sm font-medium text-white">
              <input
                type="checkbox"
                checked={filters.nonstopOnly}
                onChange={(e) => dispatch(flightFiltersChanged({ nonstopOnly: e.target.checked }))}
                className="h-4 w-4 rounded border-white/30 accent-brand-teal"
              />
              Direkt uçuşlar
            </label>
          </div>

          <div className="flex flex-wrap items-end gap-2">
            <div className="grid flex-1 basis-44 gap-1.5 sm:max-w-56 sm:flex-none sm:basis-auto">
              <Label htmlFor="flight-origin">Nereden</Label>
              <Input
                id="flight-origin"
                value={origin}
                onChange={(e) => setOrigin(e.target.value)}
                placeholder="Ülke, şehir veya havalimanı"
                required
                className={cn('sm:w-56', heroFieldClass)}
              />
            </div>
            <button
              type="button"
              onClick={swapPlaces}
              aria-label="Kalkış ve varış yerlerini değiştir"
              className="hidden h-12 w-10 shrink-0 items-center justify-center rounded-lg border border-white/20 bg-white/10 text-white backdrop-blur-sm transition-colors hover:bg-white/20 sm:flex"
            >
              <ArrowRightLeft className="h-4 w-4" aria-hidden="true" />
            </button>
            <div className="grid flex-1 basis-44 gap-1.5 sm:max-w-56 sm:flex-none sm:basis-auto">
              <Label htmlFor="flight-destination">Nereye</Label>
              <Input
                id="flight-destination"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                placeholder="Ülke, şehir veya havalimanı"
                required
                className={cn('sm:w-56', heroFieldClass)}
              />
            </div>
            {/* Tarih alanına tıklayınca temalı takvim açılır; gidiş-dönüşte
                gidiş + dönüş yan yana tek aralık takvimini paylaşır. */}
            {tripType === 'round_trip' ? (
              <DateRangePicker
                checkIn={departDate}
                checkOut={returnDate}
                onChange={(depart, ret) => {
                  setDepartDate(depart)
                  setReturnDate(ret)
                }}
                checkInId="flight-depart"
                checkOutId="flight-return"
                checkInLabel="Gidiş tarihi"
                checkOutLabel="Dönüş tarihi"
                fieldClassName={heroFieldClass}
                required
                // Uçuşta gidiş-dönüş bir konaklama aralığı değil — ara günler boyanmaz.
                endpointsOnly
                // Tarih alanları formun sağına yakın — sola hizalı panel taşar.
                align="right"
              />
            ) : (
              <DatePicker
                id="flight-depart"
                label="Gidiş tarihi"
                value={departDate}
                onChange={setDepartDate}
                required
                fieldClassName={heroFieldClass}
              />
            )}
            <PeoplePicker
              id="flight-passengers"
              label="Yolcu"
              summary={`${passengers} yolcu`}
              rows={[
                { key: 'passengers', label: 'Yolcu', value: passengers, min: 1, max: 9 },
              ]}
              onRowChange={(_, v) => setPassengers(v)}
              fieldClassName={cn('w-32', heroFieldClass)}
              align="right"
            />
            {/* Yükseklik hero alanlarıyla (h-12) eşit — items-end satırında üst/alt hizalı. */}
            <Button type="submit" className="h-12">
              Ara
            </Button>
          </div>
        </form>
      </SearchHero>

      {!criteria && (
        <EmptyState tone="dark" title="Aramaya hazır" icon={<Plane className="h-5 w-5" />}>
          Sonuçları görmek için arama kriterlerini girin.
        </EmptyState>
      )}

      {query.isFetching && (
        <div className="space-y-3">
          <LoadingState label="Aranıyor…" className="text-brand-ice/70" />
          {/* Dekoratif iskelet kartlar — duyuruyu üstteki role="status" yapar. */}
          <div aria-hidden="true" className="grid gap-3">
            <Skeleton className="h-36" />
            <Skeleton className="h-36" />
            <Skeleton className="h-36" />
          </div>
        </div>
      )}

      {query.isError && !query.isFetching && (
        <ErrorState message={query.error.message} onRetry={() => query.refetch()} />
      )}

      {query.data && (
        <>
          <FlightFilters airlines={airlines} />
          <p className="text-sm text-brand-ice/70">{visible.length} sonuç</p>
          <FlightList products={visible} />
        </>
      )}
    </div>
  )
}
