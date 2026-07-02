import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { CalendarDays } from 'lucide-react'
import { DayPicker, type DateRange } from 'react-day-picker'
import { format, isValid, parseISO } from 'date-fns'
import { tr } from 'date-fns/locale'
import 'react-day-picker/style.css'
import { Button } from '@/components/ui/button'
import { BRAND } from '@/lib/brand'

/**
 * Tarih aralığı takvimi — progressive enhancement: native <input type="date">
 * alanları otoriter kalır (testler ve klavye/ekran okuyucu akışı onların
 * üzerinden), bu popover yalnızca AYNI state'e yazan görsel bir kısayoldur.
 * Koyu cam panel + teal aralık vurgusu (gece uçuşu yüzeyi için).
 */
interface DateRangePickerProps {
  checkIn: string
  checkOut: string
  onChange: (checkIn: string, checkOut: string) => void
}

function parseDay(value: string): Date | undefined {
  if (!value) return undefined
  const parsed = parseISO(value)
  return isValid(parsed) ? parsed : undefined
}

export function DateRangePicker({ checkIn, checkOut, onChange }: DateRangePickerProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false)
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const from = parseDay(checkIn)
  const to = parseDay(checkOut)
  const selected: DateRange | undefined = from ? { from, to } : undefined

  return (
    <div ref={rootRef} className="relative">
      <Button
        type="button"
        variant="outline"
        aria-haspopup="dialog"
        aria-expanded={open}
        className="border-white/15 bg-white/5 text-brand-ice hover:border-brand-teal hover:bg-white/10 hover:text-white"
        onClick={() => setOpen((o) => !o)}
      >
        {/* Metin "seç" içermemeli — kart testleri getByRole(name: /seç/i) kullanıyor. */}
        <CalendarDays className="h-4 w-4" aria-hidden />
        Takvim
      </Button>

      {open && (
        <div
          role="dialog"
          aria-label="Tarih aralığı seç"
          className="absolute left-0 top-full z-50 mt-2 w-max max-w-[calc(100vw-2rem)] rounded-2xl border border-white/15 bg-brand-navy/95 p-3 text-white shadow-2xl backdrop-blur-md"
          style={
            {
              '--rdp-accent-color': BRAND.teal,
              '--rdp-accent-background-color': `${BRAND.teal}26`, // %15 alfa
              '--rdp-today-color': BRAND.ice,
            } as CSSProperties
          }
        >
          <DayPicker
            mode="range"
            numberOfMonths={2}
            locale={tr}
            selected={selected}
            defaultMonth={from}
            onSelect={(range) => {
              // rdp ilk tıklamada {from: X, to: X} döndürür — aynı gün "aralık
              // tamam" değildir (0 gecelik konaklama yok): çıkışı boş bırak, paneli açık tut.
              const start = range?.from
              const end =
                range?.to && start && range.to.getTime() !== start.getTime()
                  ? range.to
                  : undefined
              onChange(
                start ? format(start, 'yyyy-MM-dd') : '',
                end ? format(end, 'yyyy-MM-dd') : '',
              )
              if (start && end) setOpen(false)
            }}
          />
        </div>
      )}
    </div>
  )
}

export default DateRangePicker
