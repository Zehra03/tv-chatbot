import { useMemo, useState, type FormEvent } from 'react'
import { Plane } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { DatePicker } from '@/components/ui/date-picker'
import { DateRangePicker } from '@/components/ui/date-range-picker'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SplitText } from '@/components/SplitText'
import { Skeleton } from '@/components/ui/skeleton'
import { useAppSelector } from '@/app/hooks'
import { darkFieldClass, darkPrimaryButtonClass } from '@/lib/field-styles'
import { useFlightSearch } from '@/features/flights/useFlightSearch'
import { FlightFilters } from '@/features/flights/FlightFilters'
import { FlightList } from '@/features/flights/FlightList'
import type { FlightSearchCriteria, TripType } from '@/types'

/**
 * /flights — filtrelenebilir uçuş sonuç ekranı (docs/frontend-architecture.md §3).
 * Form kriterleri belirler (chat'te birikenlerle ön-doldurulur), arama React
 * Query ile kritere key'li çalışır, uiSlice filtreleri sonuca istemci
 * tarafında uygulanır.
 */
export function FlightsPage() {
  const chatCriteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  const prefill = chatCriteria?.intent === 'flight' ? chatCriteria.criteria : undefined

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

  return (
    <div className="space-y-6">
      <div>
        <SplitText
          text="Uçuşlar"
          tag="h1"
          textAlign="left"
          className="text-2xl font-bold text-white"
          delay={40}
          duration={0.8}
        />
        <p className="text-sm text-brand-ice/70">
          Kriterlere göre ara; sonuçları aktarma, havayolu ve fiyata göre daralt.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="flight-origin">Nereden</Label>
          <Input
            id="flight-origin"
            value={origin}
            onChange={(e) => setOrigin(e.target.value)}
            placeholder="Kalkış şehri"
            required
            className={darkFieldClass}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="flight-destination">Nereye</Label>
          <Input
            id="flight-destination"
            value={destination}
            onChange={(e) => setDestination(e.target.value)}
            placeholder="Varış şehri"
            required
            className={darkFieldClass}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="flight-triptype">Yön</Label>
          <DropdownSelect
            id="flight-triptype"
            value={tripType}
            options={[
              { value: 'one_way', label: 'Tek yön' },
              { value: 'round_trip', label: 'Gidiş-dönüş' },
            ]}
            onChange={(v) => setTripType(v as TripType)}
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
            fieldClassName={darkFieldClass}
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
            fieldClassName={darkFieldClass}
          />
        )}
        <div className="grid gap-1.5">
          <Label htmlFor="flight-passengers">Yolcu</Label>
          <Input
            id="flight-passengers"
            type="number"
            min={1}
            className={`w-24 ${darkFieldClass}`}
            value={passengers}
            onChange={(e) => setPassengers(Math.max(1, Number(e.target.value)))}
          />
        </div>
        <Button type="submit" className={darkPrimaryButtonClass}>
          Ara
        </Button>
      </form>

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
