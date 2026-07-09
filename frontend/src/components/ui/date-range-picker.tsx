import {
  useEffect,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
} from 'react'
import { type DateRange } from 'react-day-picker'
import { format, isValid, parseISO } from 'date-fns'
import { Calendar, calendarPopoverClass } from '@/components/ui/calendar'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/**
 * Tarih aralığı alanları + takvim — ayrı bir "Takvim" butonu yoktur; giriş/çıkış
 * alanına tıklandığında popover açılır. Native <input type="date"> readOnly:
 * tarayıcının kendi tarih seçicisi (ikinci, gezilemez takvim) hiçbir ortamda
 * açılmaz; değerleri yalnızca temalı takvim yazar. Etiket + değer erişilebilir
 * kalır, testler değeri programatik change ile set eder.
 * Tema: components/ui/calendar.tsx (koyu cam panel + teal aralık vurgusu).
 */
interface DateRangePickerProps {
  checkIn: string
  checkOut: string
  onChange: (checkIn: string, checkOut: string) => void
  /** Alan id'leri — <Label htmlFor> ilişkisi için. */
  checkInId?: string
  checkOutId?: string
  checkInLabel?: string
  checkOutLabel?: string
  /** Native alanlara giydirilecek ek sınıf (ör. koyu yüzey cam görünümü). */
  fieldClassName?: string
  required?: boolean
  /** true → aralık arası vurgulanmaz, yalnızca iki uç tarih işaretlenir. */
  endpointsOnly?: boolean
  /** Popover hizası — form sağ kenara yakınsa 'right' taşmayı önler. */
  align?: 'left' | 'right'
}

function parseDay(value: string): Date | undefined {
  if (!value) return undefined
  const parsed = parseISO(value)
  return isValid(parsed) ? parsed : undefined
}

export function DateRangePicker({
  checkIn,
  checkOut,
  onChange,
  checkInId = 'date-range-checkin',
  checkOutId = 'date-range-checkout',
  checkInLabel = 'Giriş',
  checkOutLabel = 'Çıkış',
  fieldClassName,
  required,
  endpointsOnly,
  align = 'left',
}: DateRangePickerProps) {
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

  // Native picker yerine bizim takvim: alan readOnly (native seçici hiç açılmaz),
  // mousedown'da preventDefault artık yalnızca segment odağını/parlamayı bastırır.
  const fieldClass = cn('[&::-webkit-calendar-picker-indicator]:hidden', fieldClassName)
  const openCalendar = (e: ReactMouseEvent<HTMLInputElement>) => {
    e.preventDefault()
    setOpen(true)
  }

  return (
    <div ref={rootRef} className="relative flex items-end gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor={checkInId}>{checkInLabel}</Label>
        <Input
          id={checkInId}
          type="date"
          value={checkIn}
          readOnly
          onChange={(e) => onChange(e.target.value, checkOut)}
          onMouseDown={openCalendar}
          aria-haspopup="dialog"
          aria-expanded={open}
          required={required}
          className={fieldClass}
        />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor={checkOutId}>{checkOutLabel}</Label>
        <Input
          id={checkOutId}
          type="date"
          value={checkOut}
          min={checkIn || undefined}
          readOnly
          onChange={(e) => onChange(checkIn, e.target.value)}
          onMouseDown={openCalendar}
          aria-haspopup="dialog"
          aria-expanded={open}
          required={required}
          className={fieldClass}
        />
      </div>

      {open && (
        <div
          role="dialog"
          aria-label="Tarih aralığı seç"
          className={cn(calendarPopoverClass, align === 'right' ? 'right-0' : 'left-0')}
        >
          <Calendar
            mode="range"
            numberOfMonths={2}
            endpointsOnly={endpointsOnly}
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
