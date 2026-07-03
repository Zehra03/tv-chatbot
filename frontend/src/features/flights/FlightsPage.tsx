import { useMemo, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { useAppSelector } from '@/app/hooks'
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
        <h1 className="text-2xl font-bold">Uçuşlar</h1>
        <p className="text-sm text-muted-foreground">
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
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="flight-depart">Gidiş tarihi</Label>
          <Input
            id="flight-depart"
            type="date"
            value={departDate}
            onChange={(e) => setDepartDate(e.target.value)}
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="flight-triptype">Yön</Label>
          <NativeSelect
            id="flight-triptype"
            value={tripType}
            onChange={(e) => setTripType(e.target.value as TripType)}
          >
            <option value="one_way">Tek yön</option>
            <option value="round_trip">Gidiş-dönüş</option>
          </NativeSelect>
        </div>
        {tripType === 'round_trip' && (
          <div className="grid gap-1.5">
            <Label htmlFor="flight-return">Dönüş tarihi</Label>
            <Input
              id="flight-return"
              type="date"
              value={returnDate}
              onChange={(e) => setReturnDate(e.target.value)}
            />
          </div>
        )}
        <div className="grid gap-1.5">
          <Label htmlFor="flight-passengers">Yolcu</Label>
          <Input
            id="flight-passengers"
            type="number"
            min={1}
            className="w-24"
            value={passengers}
            onChange={(e) => setPassengers(Math.max(1, Number(e.target.value)))}
          />
        </div>
        <Button type="submit">Ara</Button>
      </form>

      {!criteria && <EmptyState>Sonuçları görmek için arama kriterlerini girin.</EmptyState>}

      {query.isFetching && <LoadingState label="Aranıyor…" />}

      {query.isError && !query.isFetching && (
        <ErrorState message={query.error.message} onRetry={() => query.refetch()} />
      )}

      {query.data && (
        <>
          <FlightFilters airlines={airlines} />
          <p className="text-sm text-muted-foreground">{visible.length} sonuç</p>
          <FlightList products={visible} />
        </>
      )}
    </div>
  )
}
