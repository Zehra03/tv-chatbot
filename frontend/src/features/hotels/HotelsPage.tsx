import { useMemo, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { useAppSelector } from '@/app/hooks'
import { useHotelSearch } from '@/features/hotels/useHotelSearch'
import { HotelFilters } from '@/features/hotels/HotelFilters'
import { HotelList } from '@/features/hotels/HotelList'
import type { HotelSearchCriteria } from '@/types'

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
  const [criteria, setCriteria] = useState<HotelSearchCriteria | null>(null)

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
      children: 0,
      childAges: [],
      rooms: 1,
      nationality: 'TR',
      currency: 'EUR',
    })
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Oteller</h1>
        <p className="text-sm text-muted-foreground">
          Kriterlere göre ara; sonuçları yıldız, pansiyon ve fiyata göre daralt.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="hotel-destination">Nereye</Label>
          <Input
            id="hotel-destination"
            value={destination}
            onChange={(e) => setDestination(e.target.value)}
            placeholder="Şehir veya bölge"
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="hotel-checkin">Giriş</Label>
          <Input
            id="hotel-checkin"
            type="date"
            value={checkIn}
            onChange={(e) => setCheckIn(e.target.value)}
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="hotel-checkout">Çıkış</Label>
          <Input
            id="hotel-checkout"
            type="date"
            value={checkOut}
            onChange={(e) => setCheckOut(e.target.value)}
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="hotel-adults">Yetişkin</Label>
          <Input
            id="hotel-adults"
            type="number"
            min={1}
            className="w-24"
            value={adults}
            onChange={(e) => setAdults(Math.max(1, Number(e.target.value)))}
          />
        </div>
        <Button type="submit">Ara</Button>
      </form>

      {!criteria && (
        <p className="text-sm text-muted-foreground">
          Sonuçları görmek için arama kriterlerini girin.
        </p>
      )}

      {query.isLoading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Spinner size={16} />
          Aranıyor…
        </div>
      )}

      {query.isError && (
        <p role="alert" className="flex items-center gap-3 text-sm text-destructive">
          {query.error.message}
          <Button type="button" variant="outline" size="sm" onClick={() => query.refetch()}>
            Tekrar dene
          </Button>
        </p>
      )}

      {query.data && (
        <>
          <HotelFilters boardTypes={boardTypes} />
          <p className="text-sm text-muted-foreground">{visible.length} sonuç</p>
          <HotelList products={visible} />
        </>
      )}
    </div>
  )
}
