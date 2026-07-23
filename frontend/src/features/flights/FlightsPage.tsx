import { useMemo, useState, type FormEvent } from 'react'
import { format } from 'date-fns'
import { ArrowRightLeft, Plane, SlidersHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { DatePicker } from '@/components/ui/date-picker'
import { DateRangePicker } from '@/components/ui/date-range-picker'
import { PeoplePicker } from '@/components/ui/people-picker'
import { ActiveFilterChips } from '@/components/ActiveFilterChips'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SearchHero } from '@/components/SearchHero'
import { FlightCardSkeleton } from '@/features/flights/FlightCardSkeleton'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { heroFieldClass } from '@/lib/field-styles'
import { cn } from '@/lib/utils'
import { flightFiltersChanged, flightFiltersReset } from '@/features/ui/uiSlice'
import { flightFilterChips } from '@/features/ui/filterChips'
import { useFlightSearch } from '@/features/flights/useFlightSearch'
import { FlightFilters } from '@/features/flights/FlightFilters'
import { FlightList } from '@/features/flights/FlightList'
import { LocationAutocomplete } from '@/features/flights/LocationAutocomplete'
import { flightApi } from '@/api'
import { MAX_PARTY_SIZE, FLIGHT_CHILD_MAX_AGE, FLIGHT_INFANT_MAX_AGE } from '@/types'
import type { FlightLocation, FlightSearchCriteria, TripType } from '@/types'
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
  // Autocomplete'ten seçilen TourVisio konum id'si (ör. "AYT"). Serbest metin
  // yazılınca temizlenir; yalnızca listeden seçilen konum aramaya gider (kullanıcı
  // kafasına göre metin yazıp arayamaz). Chat'ten ön-dolan kriter zaten çözülmüş
  // bir konumdur — kodu ön-değerle başlatırız ki handoff aramayı bozmasın.
  const [originCode, setOriginCode] = useState<string | null>(prefill?.origin ?? null)
  const [destinationCode, setDestinationCode] = useState<string | null>(prefill?.destination ?? null)
  const [departDate, setDepartDate] = useState(prefill?.departDate ?? '')
  const [returnDate, setReturnDate] = useState(prefill?.returnDate ?? '')
  const [adults, setAdults] = useState(prefill?.adults ?? 1)
  // Chat'ten tek bir birikmiş childAges gelir (infant/çocuk ayrımını backend yaşa göre yapar);
  // burada yaş eşiğine göre iki ayrı sayaca bölünür ki "Çocuk" ve "Bebek" formda AYRI gösterilsin
  // (canlıda ikisi tek "çocuk/bebek" sayacı altında karışıyordu — bebek çocuktan ayrı olmalı).
  const childPrefillAges = (prefill?.childAges ?? []).filter((age) => age > FLIGHT_INFANT_MAX_AGE)
  const infantPrefillAges = (prefill?.childAges ?? []).filter((age) => age <= FLIGHT_INFANT_MAX_AGE)
  const [childCount, setChildCount] = useState(childPrefillAges.length)
  const [childAges, setChildAges] = useState<number[]>(childPrefillAges)
  const [infantCount, setInfantCount] = useState(infantPrefillAges.length)
  const [infantAges, setInfantAges] = useState<number[]>(infantPrefillAges)
  const [tripType, setTripType] = useState<TripType>(prefill?.tripType ?? 'one_way')
  const [criteria, setCriteria] = useState<FlightSearchCriteria | null>(null)
  // Sınırda (submit) doğrulama mesajı — geçersiz kriterde arama tetiklenmez.
  const [formError, setFormError] = useState<string | null>(null)

  // childAges/infantAges uzunluğu her zaman ilgili sayaçla tutarlı tutulur (types/search.ts
  // invariantı) — otel formundakiyle aynı desen (HotelsPage.changeChildCount).
  const changeChildCount = (next: number) => {
    setChildCount(next)
    setChildAges((ages) =>
      next > ages.length ? [...ages, ...Array<number>(next - ages.length).fill(7)] : ages.slice(0, next),
    )
  }

  const changeInfantCount = (next: number) => {
    setInfantCount(next)
    setInfantAges((ages) =>
      next > ages.length ? [...ages, ...Array<number>(next - ages.length).fill(0)] : ages.slice(0, next),
    )
  }

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

  // Aktif filtre çipleri: kaldırma, filtreyi boşa çeken kısmi güncellemeyi dispatch eder —
  // `visible` memo'su yeniden hesaplanır, arama tekrar tetiklenmez (filtreler istemci tarafında).
  const chips = useMemo(
    () =>
      flightFilterChips(filters).map((chip) => ({
        key: chip.key,
        label: chip.label,
        onRemove: () => dispatch(flightFiltersChanged(chip.clear)),
      })),
    [filters, dispatch],
  )

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()

    // Sınır doğrulaması: geçersiz kriter aramaya/backend'e gitmesin; kullanıcı
    // sessiz bir "hiçbir şey olmadı" yerine net bir mesaj görsün.
    // Listeden seçildiyse TourVisio konum id'si; değilse yazılan serbest metin.
    const from = originCode ?? origin.trim()
    const to = destinationCode ?? destination.trim()

    if (!origin.trim() || !destination.trim()) {
      setFormError('Lütfen kalkış ve varış yerlerini girin.')
      return
    }
    // Destinasyon yalnızca dropdown önerisinden seçilebilir: seçilmemiş serbest
    // metin (kod yok) aramaya gitmez; kullanıcı listeden bir konum seçmeli.
    if (!originCode) {
      setFormError('Lütfen kalkış yerini listeden seçin.')
      return
    }
    if (!destinationCode) {
      setFormError('Lütfen varış yerini listeden seçin.')
      return
    }
    if (from.toLowerCase() === to.toLowerCase()) {
      setFormError('Kalkış ve varış yeri aynı olamaz.')
      return
    }
    if (!departDate) {
      setFormError('Lütfen gidiş tarihini seçin.')
      return
    }
    // ISO 'yyyy-MM-dd' dizeleri sözlük sırası = tarih sırası; güvenle kıyaslanır.
    const todayStr = format(new Date(), 'yyyy-MM-dd')
    if (departDate < todayStr) {
      setFormError('Gidiş tarihi geçmişte olamaz.')
      return
    }
    if (tripType === 'round_trip') {
      if (!returnDate) {
        setFormError('Gidiş-dönüş seçtiniz; lütfen dönüş tarihini de seçin.')
        return
      }
      if (returnDate < departDate) {
        setFormError('Dönüş tarihi gidiş tarihinden önce olamaz.')
        return
      }
    }

    setFormError(null)
    setCriteria({
      origin: from,
      destination: to,
      departDate,
      adults,
      // Backend tek bir childAges listesi bekler; infant/çocuk ücret tipini yaşa göre
      // kendisi ayırır (PassengerCount.ofChildAges) — iki sayaç burada birleşir.
      childAges: [...childAges, ...infantAges],
      currency: 'EUR',
      tripType,
      ...(tripType === 'round_trip' && returnDate ? { returnDate } : {}),
    })
  }

  const handleOriginSelect = (loc: FlightLocation) => {
    setOrigin(loc.name)
    setOriginCode(loc.id)
  }

  const handleDestinationSelect = (loc: FlightLocation) => {
    setDestination(loc.name)
    setDestinationCode(loc.id)
  }

  const swapPlaces = () => {
    setOrigin(destination)
    setDestination(origin)
    setOriginCode(destinationCode)
    setDestinationCode(originCode)
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
            {/* Hero örneği: örtü her temada lacivert, o yüzden cam yüzeyi de
                temadan bağımsız beyaz alfa (bkz. SearchHero / heroFieldClass).
                Metin rengi verilmiyor — SearchHero'dan beyaz miras alınır. */}
            <DropdownSelect
              id="flight-triptype"
              aria-label="Yön"
              value={tripType}
              options={[
                { value: 'one_way', label: 'Tek yön' },
                { value: 'round_trip', label: 'Gidiş-dönüş' },
              ]}
              onChange={(v) => setTripType(v as TripType)}
              className="border-white/15 bg-white/10 hover:bg-white/20"
            />
            {/* Sonuç filtre çubuğuyla aynı uiSlice alanına yazar — ikisi senkron kalır. */}
            {/* Hero'nun lacivert örtüsü üstünde — renkler temadan bağımsız beyaz
                (bkz. components/SearchHero). */}
            <label className="flex items-center gap-2 text-sm font-medium text-white">
              <input
                type="checkbox"
                checked={filters.nonstopOnly}
                onChange={(e) => dispatch(flightFiltersChanged({ nonstopOnly: e.target.checked }))}
                className="h-4 w-4 rounded border-white/30 accent-brand-steel [color-scheme:dark]"
              />
              Direkt uçuşlar
            </label>
          </div>

          <div className="flex flex-wrap items-end gap-2">
            <div className="flex-1 basis-44 sm:max-w-56 sm:flex-none sm:basis-auto">
              <LocationAutocomplete
                id="flight-origin"
                label="Nereden"
                fetchSuggestions={(q) => flightApi.locations(q, 'departure')}
                queryKeyBase="flight-locations-departure"
                value={origin}
                onTextChange={(text) => {
                  setOrigin(text)
                  setOriginCode(null)
                }}
                onSelect={handleOriginSelect}
                placeholder="Ülke, şehir veya havalimanı"
                required
                fieldClassName={heroFieldClass}
                className="sm:w-56"
              />
            </div>
            <button
              type="button"
              onClick={swapPlaces}
              aria-label="Kalkış ve varış yerlerini değiştir"
              className="hidden h-12 w-10 shrink-0 items-center justify-center rounded-lg border border-white/20 bg-white/10 text-white transition-colors hover:bg-white/20 sm:flex"
            >
              <ArrowRightLeft className="h-4 w-4" aria-hidden="true" />
            </button>
            <div className="flex-1 basis-44 sm:max-w-56 sm:flex-none sm:basis-auto">
              <LocationAutocomplete
                id="flight-destination"
                label="Nereye"
                fetchSuggestions={(q) => flightApi.locations(q, 'arrival')}
                queryKeyBase="flight-locations-arrival"
                value={destination}
                onTextChange={(text) => {
                  setDestination(text)
                  setDestinationCode(null)
                }}
                onSelect={handleDestinationSelect}
                placeholder="Ülke, şehir veya havalimanı"
                required
                fieldClassName={heroFieldClass}
                className="sm:w-56"
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
                // Geçmiş günler takvimde seçilemez (gidiş de dönüş de).
                disablePast
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
                disablePast
                fieldClassName={heroFieldClass}
              />
            )}
            {/* HATA 5 (düzeltildi): tek "yolcu sayısı" yerine yetişkin + çocuk + bebek (yaş
                bazlı) — otel formundaki misafir picker'ıyla aynı desen. Çocuk ve bebek AYRI
                sayaçlardır (canlıda ikisi tek "çocuk/bebek" altında karışıyordu); yaş backend'de
                PassengerCount.ofChildAges ile ücret tipini (infant/child/adult) belirler. */}
            <PeoplePicker
              id="flight-passengers"
              label="Yolcu"
              summary={`${adults} yetişkin${childCount ? `, ${childCount} çocuk` : ''}${infantCount ? `, ${infantCount} bebek` : ''}`}
              rows={[
                { key: 'adults', label: 'Yetişkin', hint: '12 yaş ve üzeri', value: adults, min: 1, max: MAX_PARTY_SIZE },
                { key: 'children', label: 'Çocuk', hint: `${FLIGHT_INFANT_MAX_AGE + 1}–${FLIGHT_CHILD_MAX_AGE} yaş`, value: childCount, min: 0, max: 6 },
                { key: 'infants', label: 'Bebek', hint: `0–${FLIGHT_INFANT_MAX_AGE} yaş, kucakta`, value: infantCount, min: 0, max: 6 },
              ]}
              onRowChange={(key, v) => {
                if (key === 'adults') setAdults(v)
                else if (key === 'children') changeChildCount(v)
                else changeInfantCount(v)
              }}
              fieldClassName={cn('w-40', heroFieldClass)}
              align="right"
            >
              {childCount > 0 && (
                <div className="mt-4 border-t border-border pt-3">
                  <p className="text-xs font-medium text-muted-foreground">
                    Çocuk yaşları (ücret tipi için)
                  </p>
                  <div className="mt-2 grid grid-cols-[repeat(auto-fit,minmax(6.5rem,1fr))] gap-2">
                    {childAges.map((age, i) => (
                      <label
                        key={i}
                        className="grid min-w-0 grid-rows-[auto_auto] gap-1 text-start text-xs text-muted-foreground"
                      >
                        <span className="truncate">{i + 1}. çocuğun yaşı</span>
                        <select
                          value={age}
                          onChange={(e) =>
                            setChildAges((ages) =>
                              ages.map((a, j) => (j === i ? Number(e.target.value) : a)),
                            )
                          }
                          className="h-9 w-full min-w-0 rounded-md border border-border bg-background px-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                        >
                          {Array.from(
                            { length: FLIGHT_CHILD_MAX_AGE - FLIGHT_INFANT_MAX_AGE },
                            (_, y) => y + FLIGHT_INFANT_MAX_AGE + 1,
                          ).map((y) => (
                            <option key={y} value={y} className="bg-background text-foreground">
                              {y}
                            </option>
                          ))}
                        </select>
                      </label>
                    ))}
                  </div>
                </div>
              )}
              {infantCount > 0 && (
                <div className="mt-4 border-t border-border pt-3">
                  <p className="text-xs font-medium text-muted-foreground">
                    Bebek yaşları (ücret tipi için)
                  </p>
                  <div className="mt-2 grid grid-cols-[repeat(auto-fit,minmax(6.5rem,1fr))] gap-2">
                    {infantAges.map((age, i) => (
                      <label
                        key={i}
                        className="grid min-w-0 grid-rows-[auto_auto] gap-1 text-start text-xs text-muted-foreground"
                      >
                        <span className="truncate">{i + 1}. bebeğin yaşı</span>
                        <select
                          value={age}
                          onChange={(e) =>
                            setInfantAges((ages) =>
                              ages.map((a, j) => (j === i ? Number(e.target.value) : a)),
                            )
                          }
                          className="h-9 w-full min-w-0 rounded-md border border-border bg-background px-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                        >
                          {Array.from({ length: FLIGHT_INFANT_MAX_AGE + 1 }, (_, y) => (
                            <option key={y} value={y} className="bg-background text-foreground">
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
            {/* Yükseklik hero alanlarıyla (h-12) eşit — items-end satırında üst/alt hizalı.
                text-white: Button'ın default varyantı text-foreground'dur; hero'nun
                lacivert örtüsünde açık temada siyaha dönerdi (bkz. SearchHero). */}
            <Button type="submit" variant="cta" className="h-12">
              Ara
            </Button>
          </div>

          {/* Doğrulama mesajı — role="alert" ile ekran okuyucuya anons edilir. */}
          {formError && (
            <p
              role="alert"
              className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm font-medium text-destructive-emphasis"
            >
              {formError}
            </p>
          )}
        </form>
      </SearchHero>

      {!criteria && (
        <EmptyState tone="dark" title="Aramaya hazır" icon={<Plane className="h-5 w-5" />}>
          Sonuçları görmek için arama kriterlerini girin.
        </EmptyState>
      )}

      {query.isFetching && (
        <div className="space-y-3">
          <LoadingState label="Aranıyor…" className="text-muted-foreground" />
          {/* Karta birebir iskelet kartlar (CLS 0) — duyuruyu üstteki role="status" yapar. */}
          <div aria-hidden="true" className="grid gap-3">
            <FlightCardSkeleton />
            <FlightCardSkeleton />
            <FlightCardSkeleton />
          </div>
        </div>
      )}

      {query.isError && !query.isFetching && (
        <ErrorState message={apiErrorMessage(query.error)} onRetry={() => query.refetch()} />
      )}

      {query.data && (
        <>
          <FlightFilters airlines={airlines} />
          <ActiveFilterChips chips={chips} />
          <p className="text-sm text-muted-foreground">{visible.length} sonuç</p>
          {/* Filtreler tüm sonuçları eleyince pasif "bulunamadı" yerine tıklanabilir
              kurtarma (§6): backend uçuş döndü ama filtreler 0'a indirdi → tek tıkla temizle. */}
          {query.data.length > 0 && visible.length === 0 ? (
            <EmptyState
              tone="dark"
              title="Filtrelerinizle eşleşen uçuş yok"
              icon={<SlidersHorizontal className="h-5 w-5" />}
              action={
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => dispatch(flightFiltersReset())}
                >
                  Filtreleri temizle
                </Button>
              }
            >
              Toplam {query.data.length} uçuş bulundu ama seçili filtreler hepsini eledi.
            </EmptyState>
          ) : (
            <FlightList products={visible} criteria={criteria ?? undefined} />
          )}
        </>
      )}
    </div>
  )
}
