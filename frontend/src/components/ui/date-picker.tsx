import {
  useEffect,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
} from 'react'
import { format, isValid, parseISO } from 'date-fns'
import { Calendar, calendarPopoverClass } from '@/components/ui/calendar'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/**
 * Tek tarih alanı + takvim — DateRangePicker'ın tek günlük kardeşi (tek yön
 * uçuş gibi aralık gerektirmeyen alanlar için). Alana tıklandığında temalı
 * takvim açılır; native <input type="date"> otoriter kalır.
 */
interface DatePickerProps {
  id?: string
  label: string
  value: string
  onChange: (value: string) => void
  min?: string
  required?: boolean
  /** Native alana giydirilecek ek sınıf (ör. koyu yüzey cam görünümü). */
  fieldClassName?: string
}

function parseDay(value: string): Date | undefined {
  if (!value) return undefined
  const parsed = parseISO(value)
  return isValid(parsed) ? parsed : undefined
}

export function DatePicker({
  id = 'date-picker',
  label,
  value,
  onChange,
  min,
  required,
  fieldClassName,
}: DatePickerProps) {
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

  const selected = parseDay(value)

  const openCalendar = (e: ReactMouseEvent<HTMLInputElement>) => {
    e.preventDefault()
    setOpen(true)
  }

  return (
    <div ref={rootRef} className="relative grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type="date"
        value={value}
        min={min}
        onChange={(e) => onChange(e.target.value)}
        onMouseDown={openCalendar}
        aria-haspopup="dialog"
        aria-expanded={open}
        required={required}
        className={cn('[&::-webkit-calendar-picker-indicator]:hidden', fieldClassName)}
      />

      {open && (
        <div role="dialog" aria-label="Tarih seç" className={calendarPopoverClass}>
          <Calendar
            mode="single"
            numberOfMonths={1}
            selected={selected}
            defaultMonth={selected}
            onSelect={(day) => {
              onChange(day ? format(day, 'yyyy-MM-dd') : '')
              if (day) setOpen(false)
            }}
          />
        </div>
      )}
    </div>
  )
}

export default DatePicker
