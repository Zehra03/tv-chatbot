import { useMemo, useState, type FormEvent } from 'react'
import { Hotel, SlidersHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ActiveFilterChips } from '@/components/ActiveFilterChips'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { SearchHero } from '@/components/SearchHero'
import { HotelCardSkeleton } from '@/features/hotels/HotelCardSkeleton'
import { DateRangePicker } from '@/components/ui/date-range-picker'
import { PeoplePicker } from '@/components/ui/people-picker'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { CHILD_MAX_AGE, HOTEL_CHILD_MIN_AGE } from '@/features/reservation/reservationFormSchema'
import { heroFieldClass } from '@/lib/field-styles'
import { cn } from '@/lib/utils'
import { hotelFiltersChanged, hotelFiltersReset } from '@/features/ui/uiSlice'
import { hotelFilterChips } from '@/features/ui/filterChips'
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
  const dispatch = useAppDispatch()
  const chatCriteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  const prefill = chatCriteria?.intent === 'hotel' ? chatCriteria.criteria : undefined

  const [destination, setDestination] = useState(prefill?.destination ?? '')
  // Destinasyon yalnızca dropdown önerisinden seçilebilir: bu bayrak seçimi izler.
  // Chat'ten ön-dolan kriter zaten çözülmüş bir konumdur → seçilmiş sayılır;
  // kullanıcı serbest metin yazınca temizlenir (aşağıda onTextChange).
  const [destinationSelected, setDestinationSelected] = useState(Boolean(prefill?.destination))
  const [checkIn, setCheckIn] = useState(prefill?.checkIn ?? '')
  const [checkOut, setCheckOut] = useState(prefill?.checkOut ?? '')
  const [adults, setAdults] = useState(prefill?.adults ?? 2)
  const [childCount, setChildCount] = useState(prefill?.children ?? 0)
  const [childAges, setChildAges] = useState<number[]>(prefill?.childAges ?? [])
  const [rooms, setRooms] = useState(prefill?.rooms ?? 1)
  const [criteria, setCriteria] = useState<HotelSearchCriteria | null>(null)
  // Sınırda (submit) doğrulama mesajı — geçersiz kriterde arama tetiklenmez.
  const [formError, setFormError] = useState<string | null>(null)

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

  // Aktif filtre çipleri: kaldırma, filtreyi boşa çeken kısmi güncellemeyi dispatch eder —
  // `visible` memo'su yeniden hesaplanır, arama tekrar tetiklenmez (filtreler istemci tarafında).
  const chips = useMemo(
    () =>
      hotelFilterChips(filters).map((chip) => ({
        key: chip.key,
        label: chip.label,
        onRemove: () => dispatch(hotelFiltersChanged(chip.clear)),
      })),
    [filters, dispatch],
  )

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!destination.trim()) {
      setFormError('Lütfen varış yerini girin.')
      return
    }
    // Serbest metin engellenir: destinasyon yalnızca dropdown önerisinden seçilebilir.
    if (!destinationSelected) {
      setFormError('Lütfen varış yerini listeden seçin.')
      return
    }
    if (!checkIn || !checkOut) {
      setFormError('Lütfen giriş ve çıkış tarihlerini seçin.')
      return
    }
    setFormError(null)
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
              onTextChange={(text) => {
                setDestination(text)
                setDestinationSelected(false)
              }}
              onSelect={(loc) => {
                setDestination(loc.name)
                setDestinationSelected(true)
              }}
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
            // Geçmiş günler takvimde seçilemez (giriş de çıkış da).
            disablePast
          />
          {/* Skyscanner'ın "Misafir ve oda sayısı" alanı: sayaçlar + çocuk yaşları. */}
          <PeoplePicker
            id="hotel-guests"
            label="Misafir ve oda"
            summary={guestSummary}
            rows={[
              { key: 'adults', label: 'Yetişkin', hint: '18 yaş ve üzeri', value: adults, min: 1, max: MAX_PARTY_SIZE },
              {
                key: 'children',
                label: 'Çocuk',
                // İpucu şemayla aynı kaynaktan (HOTEL_CHILD_MIN_AGE): otel-yalnız rezervasyonda
                // 0-2 yaş artık CHILD olarak kabul ediliyor (INFANT yalnız uçuşa özgü bir tip).
                hint: `${HOTEL_CHILD_MIN_AGE}–${CHILD_MAX_AGE} yaş`,
                value: childCount,
                min: 0,
                max: 6,
              },
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
              <div className="mt-4 border-t border-border pt-3">
                <p className="text-xs font-medium text-muted-foreground">
                  Çocuk yaşları (fiyatlama için)
                </p>
                {/* auto-fit: panel daraldığında (küçük ekranda min() ile kısalır)
                    iki sütun kendiliğinden tek sütuna iner — sabit grid-cols-2
                    bu genişlikte taşıyordu. */}
                <div className="mt-2 grid grid-cols-[repeat(auto-fit,minmax(6.5rem,1fr))] gap-2">
                  {childAges.map((age, i) => (
                    <label
                      key={i}
                      className="grid min-w-0 grid-rows-[auto_auto] gap-1 text-start text-xs text-muted-foreground"
                    >
                      {/* truncate: etiket sarmalanırsa o sütunun select'i komşusuna
                          göre aşağı kayıyordu — tek satıra sabitleyip hizayı korur. */}
                      <span className="truncate">{i + 1}. çocuğun yaşı</span>
                      <select
                        value={age}
                        onChange={(e) =>
                          setChildAges((ages) =>
                            ages.map((a, j) => (j === i ? Number(e.target.value) : a)),
                          )
                        }
                        // Zemin OPAK olmalı (yarı saydamda native seçenek listesi
                        // panelin değil OS yüzeyinin üstünde birleşiyor ve beyaz
                        // zemin + beyaz yazı çıkıyordu) — `bg-background` opak ve
                        // temayı izliyor. color-scheme artık :root/.dark'tan miras:
                        // sabit [color-scheme:dark] açık temada native listeyi
                        // zorla koyu render ediyordu.
                        className="h-9 w-full min-w-0 rounded-md border border-border bg-background px-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                      >
                        {/* Aralık şemayla TEK kaynaktan gelir (HOTEL_CHILD_MIN_AGE–CHILD_MAX_AGE).
                            Otel-yalnız rezervasyonda INFANT tipi yok, 0–2 yaş CHILD olarak
                            fiyatlanır — bu yüzden arama ve rezervasyon formu artık ikisi de 0–17
                            kabul ediyor (backend PreviewReservationCommand.ageMatchesType). */}
                        {Array.from({ length: CHILD_MAX_AGE - HOTEL_CHILD_MIN_AGE + 1 }, (_, i) => {
                          const y = HOTEL_CHILD_MIN_AGE + i
                          return (
                            <option key={y} value={y} className="bg-background text-foreground">
                              {y}
                            </option>
                          )
                        })}
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

          {/* Doğrulama mesajı — role="alert" ile ekran okuyucuya anons edilir.
              w-full: flex-wrap satırında kendi satırına iner. */}
          {formError && (
            <p
              role="alert"
              className="w-full rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm font-medium text-destructive-emphasis"
            >
              {formError}
            </p>
          )}
        </form>
      </SearchHero>

      {!criteria && (
        <EmptyState tone="dark" title="Aramaya hazır" icon={<Hotel className="h-5 w-5" />}>
          Sonuçları görmek için arama kriterlerini girin.
        </EmptyState>
      )}

      {query.isFetching && (
        <div className="space-y-3">
          <LoadingState label="Aranıyor…" className="text-muted-foreground" />
          {/* Karta birebir iskelet kartlar (CLS 0) — duyuruyu üstteki role="status" yapar. */}
          <div aria-hidden="true" className="grid gap-3">
            <HotelCardSkeleton />
            <HotelCardSkeleton />
            <HotelCardSkeleton />
          </div>
        </div>
      )}

      {query.isError && !query.isFetching && (
        <ErrorState message={apiErrorMessage(query.error)} onRetry={() => query.refetch()} />
      )}

      {query.data && (
        <>
          <HotelFilters boardTypes={boardTypes} />
          <ActiveFilterChips chips={chips} />
          <p className="text-sm text-muted-foreground">{visible.length} sonuç</p>
          {/* Filtreler tüm sonuçları eleyince pasif "bulunamadı" yerine tıklanabilir
              kurtarma (§6): backend otel döndü ama filtreler 0'a indirdi → tek tıkla temizle. */}
          {query.data.length > 0 && visible.length === 0 ? (
            <EmptyState
              tone="dark"
              title="Filtrelerinizle eşleşen otel yok"
              icon={<SlidersHorizontal className="h-5 w-5" />}
              action={
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => dispatch(hotelFiltersReset())}
                >
                  Filtreleri temizle
                </Button>
              }
            >
              Toplam {query.data.length} otel bulundu ama seçili filtreler hepsini eledi.
            </EmptyState>
          ) : (
            <HotelList products={visible} criteria={criteria ?? undefined} />
          )}
        </>
      )}
    </div>
  )
}
