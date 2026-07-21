import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Hotel, Loader2, MapPin, Plane } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useDebouncedValue } from '@/lib/useDebouncedValue'
import { cn } from '@/lib/utils'

/**
 * Kalkış/varış ya da otel destination alanı için otomatik tamamlama combobox'ı.
 * Kullanıcı yazdıkça (2+ karakter, debounce'lu) `fetchSuggestions` ile backend'den
 * gerçek TourVisio konumları çeker ve açılır listede gösterir. Seçilince `onSelect`
 * konumu üst bileşene verir (uçuşta id/AYT, otelde şehir adı gönderilir — kararı üst
 * bileşen verir). Seçmeden yazılan serbest metin de geçerli kalır (backend yine
 * çözer). WAI-ARIA combobox deseni: input role="combobox", liste role="listbox",
 * öğeler role="option". Uçuş ve otel formu bu tek bileşeni paylaşır.
 */
export interface LocationSuggestion {
  id: string
  code?: string | null
  name: string
  /** "airport" | "city" | "hotel" — ikon seçimini belirler. */
  type: string
}

interface LocationAutocompleteProps<T extends LocationSuggestion> {
  id: string
  label: string
  /** Alanda görünen metin (üst bileşen kontrol eder). */
  value: string
  /** Kullanıcı serbest metin yazdı — seçili konum varsa temizlenmeli. */
  onTextChange: (text: string) => void
  /** Kullanıcı listeden bir konum seçti. */
  onSelect: (location: T) => void
  /** Debounce'lu sorguyu backend'e taşıyan fonksiyon (flightApi/hotelApi). */
  fetchSuggestions: (query: string) => Promise<T[]>
  /** React Query cache ad alanı (ör. "flight-locations-departure", "hotel-locations"). */
  queryKeyBase: string
  placeholder?: string
  required?: boolean
  /** Hero (beyaz) alan sınıfı. */
  fieldClassName?: string
  /** Input'a ek sınıf (genişlik vb.). */
  className?: string
}

const MIN_QUERY_LENGTH = 2

function iconFor(type: string) {
  if (type === 'airport') return Plane
  if (type === 'hotel') return Hotel
  return MapPin
}

export function LocationAutocomplete<T extends LocationSuggestion>({
  id,
  label,
  value,
  onTextChange,
  onSelect,
  fetchSuggestions,
  queryKeyBase,
  placeholder,
  required,
  fieldClassName,
  className,
}: LocationAutocompleteProps<T>) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const rootRef = useRef<HTMLDivElement>(null)
  const listboxId = useId()

  const debounced = useDebouncedValue(value, 250)
  const canQuery = open && debounced.trim().length >= MIN_QUERY_LENGTH

  const { data: suggestions = [], isFetching, isError } = useQuery<T[]>({
    queryKey: [queryKeyBase, debounced.trim()],
    queryFn: () => fetchSuggestions(debounced.trim()),
    enabled: canQuery,
    staleTime: 5 * 60_000,
  })

  // Dış tıklama / Escape ile kapat (DropdownSelect ile aynı desen).
  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  const pick = (location: T) => {
    onSelect(location)
    setOpen(false)
    setActiveIndex(-1)
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false)
      setActiveIndex(-1)
      return
    }
    if (e.key === 'ArrowDown' && suggestions.length) {
      e.preventDefault()
      setOpen(true)
      setActiveIndex((i) => (i + 1) % suggestions.length)
      return
    }
    if (e.key === 'ArrowUp' && suggestions.length) {
      e.preventDefault()
      setActiveIndex((i) => (i <= 0 ? suggestions.length - 1 : i - 1))
      return
    }
    if (e.key === 'Enter' && open && activeIndex >= 0 && suggestions[activeIndex]) {
      // Öneri seçiliyken Enter formu göndermesin, konumu seçsin.
      e.preventDefault()
      pick(suggestions[activeIndex])
    }
  }

  const showPanel = open && debounced.trim().length >= MIN_QUERY_LENGTH
  const activeOptionId =
    activeIndex >= 0 && suggestions[activeIndex] ? `${listboxId}-opt-${activeIndex}` : undefined

  return (
    <div ref={rootRef} className="relative">
      <div className="grid gap-1.5">
        <Label htmlFor={id}>{label}</Label>
        <Input
          id={id}
          type="text"
          role="combobox"
          autoComplete="off"
          aria-expanded={showPanel}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={activeOptionId}
          value={value}
          placeholder={placeholder}
          required={required}
          className={cn(fieldClassName, className)}
          onChange={(e) => {
            onTextChange(e.target.value)
            setOpen(true)
            setActiveIndex(-1)
          }}
          onFocus={() => {
            if (value.trim().length >= MIN_QUERY_LENGTH) setOpen(true)
          }}
          onKeyDown={handleKeyDown}
        />
      </div>

      {showPanel && (
        <ul
          role="listbox"
          id={listboxId}
          aria-label={label}
          className="absolute left-0 top-full z-50 mt-1 max-h-72 w-full min-w-[16rem] overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 text-slate-900 shadow-lg"
        >
          {isFetching && !suggestions.length && (
            <li className="flex items-center gap-2 px-3 py-2 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              Aranıyor…
            </li>
          )}

          {isError && !isFetching && (
            <li className="px-3 py-2 text-sm text-destructive-emphasis">Öneriler yüklenemedi</li>
          )}

          {!isFetching && !isError && !suggestions.length && (
            <li className="px-3 py-2 text-sm text-slate-500">Sonuç bulunamadı</li>
          )}

          {suggestions.map((location, index) => {
            const active = index === activeIndex
            const Icon = iconFor(location.type)
            // TourVisio isim zaten "... (AYT)" ile bitiyor; kod rozetiyle tekrar
            // etmemesi için sondaki kodu etiketten ayıklarız.
            const codeSuffix = location.code ? ` (${location.code})` : ''
            const displayName =
              codeSuffix && location.name.endsWith(codeSuffix)
                ? location.name.slice(0, -codeSuffix.length)
                : location.name
            return (
              <li key={`${location.id}-${index}`} role="none">
                <button
                  type="button"
                  role="option"
                  id={`${listboxId}-opt-${index}`}
                  aria-selected={active}
                  // onMouseDown: input blur'undan önce çalışıp seçim kaybını önler.
                  onMouseDown={(e) => {
                    e.preventDefault()
                    pick(location)
                  }}
                  onMouseEnter={() => setActiveIndex(index)}
                  className={cn(
                    'flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm transition-colors',
                    active ? 'bg-slate-100' : 'hover:bg-slate-50',
                  )}
                >
                  <Icon className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  <span className="truncate">{displayName}</span>
                  {location.code && (
                    <span className="ml-auto shrink-0 text-xs font-medium text-slate-400">
                      {location.code}
                    </span>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default LocationAutocomplete
