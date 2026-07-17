import { type CSSProperties } from 'react'
import { DayPicker, type DayPickerProps } from 'react-day-picker'
import { tr } from 'date-fns/locale'
import 'react-day-picker/style.css'
import { BRAND, rgbTriplet } from '@/lib/brand'
import { cn } from '@/lib/utils'

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
  // Panel metniyle aynı renk — .pax-popover'ın tema-duyarlı ön planı.
  '--rdp-range_middle-color': 'var(--pax-popover-fg)',
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

/** Aralık bandını söndürür: orta günler + uç hücrelerin yarım gradyanları
 * şeffaflaşır, yalnızca iki uç tarihin teal dairesi kalır (uçuş gidiş-dönüş). */
const ENDPOINTS_ONLY_STYLE = {
  '--rdp-range_middle-background-color': 'transparent',
  '--rdp-range_start-background': 'none',
  '--rdp-range_end-background': 'none',
} as CSSProperties

/** Takvim popover kabuğu — cam panel (alan grubunun altına açılır).
 * Yatay hizayı (left-0 / right-0) kullanan bileşen ekler: form sağ kenara
 * yakınsa left-0 iki aylık paneli viewport dışına taşırır.
 *
 * Yüzey (kenar/zemin/metin/blur) .pax-popover reçetesinden — 4 açılır panelin
 * ortak, tema-duyarlı zemini. `shadow-2xl` KASITLI olarak kalıyor: takvim
 * diğer üçünün 0_0_20px gölgesini değil bunu kullanıyor ve utility katmanı
 * reçetenin box-shadow'unu ezer. */
export const calendarPopoverClass =
  'pax-popover absolute top-full z-50 mt-2 w-max max-w-[calc(100vw-2rem)] rounded-2xl p-3 shadow-2xl'

type CalendarProps = DayPickerProps & {
  /** true → aralık arası vurgulanmaz, yalnızca seçilen iki tarih işaretlenir. */
  endpointsOnly?: boolean
}

export function Calendar({ endpointsOnly, className, ...props }: CalendarProps) {
  const style = endpointsOnly ? { ...CALENDAR_STYLE, ...ENDPOINTS_ONLY_STYLE } : CALENDAR_STYLE
  return (
    <DayPicker
      locale={tr}
      style={style}
      // Ara günlerdeki .rdp-selected kalınlaştırmasını da sıfırla (index.css).
      className={cn(endpointsOnly && 'pax-endpoints-only', className)}
      {...props}
    />
  )
}

export default Calendar
