import { type CSSProperties } from 'react'
import { DayPicker, type DayPickerProps } from 'react-day-picker'
import { tr } from 'date-fns/locale'
import 'react-day-picker/style.css'
import { BRAND, rgbTriplet } from '@/lib/brand'

/**
 * Gece uçuşu temalı takvim — DateRangePicker/DatePicker'ın ortak gövdesi.
 *
 * ÖNEMLİ: rdp v10 tema değişkenlerini `.rdp-root` seçicisinin KENDİSİNDE
 * tanımlar; sarmalayıcı bir div'e verilen custom property'ler kalıtımla
 * gelmediğinden stylesheet'e yenilir (aralık lavanta/mavi varsayılana düşer).
 * Bu yüzden değişkenler DayPicker'ın `style` prop'una (inline, .rdp-root'a)
 * verilir — inline stil her zaman kazanır.
 */
const CALENDAR_STYLE = {
  '--rdp-accent-color': BRAND.teal,
  '--rdp-accent-background-color': `rgba(${rgbTriplet(BRAND.teal)}, 0.16)`,
  // Aralık ucu: teal daire üzerinde lacivert gün — koyu zeminde en net kontrast.
  '--rdp-range_start-color': BRAND.navy,
  '--rdp-range_end-color': BRAND.navy,
  '--rdp-range_middle-color': '#ffffff',
  '--rdp-today-color': BRAND.ice,
  // Varsayılan 44px hücreler popover için hantal — bir tık kompakt.
  '--rdp-day-height': '2.4rem',
  '--rdp-day-width': '2.4rem',
  '--rdp-day_button-height': '2.25rem',
  '--rdp-day_button-width': '2.25rem',
  '--rdp-nav_button-height': '2rem',
  '--rdp-nav_button-width': '2rem',
  '--rdp-months-gap': '1.5rem',
} as CSSProperties

/** Takvim popover kabuğu — koyu cam panel (alan grubunun altına açılır). */
export const calendarPopoverClass =
  'absolute left-0 top-full z-50 mt-2 w-max max-w-[calc(100vw-2rem)] rounded-2xl border border-white/15 bg-brand-navy/95 p-3 text-white shadow-2xl backdrop-blur-md'

export function Calendar(props: DayPickerProps) {
  return <DayPicker locale={tr} style={CALENDAR_STYLE} {...props} />
}

export default Calendar
