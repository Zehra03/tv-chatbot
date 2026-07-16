import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, Minus, Plus } from 'lucide-react'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/**
 * Skyscanner'ın "Yolcular" / "Misafir ve oda sayısı" alanı — tetikleyici,
 * seçimin özetini gösteren alan görünümlü bir buton; popover'da satır başına
 * eksi/artı sayaçlar. Panel, DropdownSelect ile aynı koyu cam dilini kullanır.
 * Ek içerik (ör. çocuk yaşları) children ile sayaçların altına eklenir.
 */
export interface PeopleRow {
  key: string
  label: string
  /** Satır altındaki küçük açıklama (ör. "0–17 yaş"). */
  hint?: string
  value: number
  min: number
  max: number
}

interface PeoplePickerProps {
  /** Tetikleyici buton id'si — <Label htmlFor> ilişkisi için. */
  id: string
  label: string
  /** Tetikleyicide görünen özet (ör. "2 yetişkin, 1 oda"). */
  summary: string
  rows: PeopleRow[]
  onRowChange: (key: string, value: number) => void
  children?: ReactNode
  /** Tetikleyiciye giydirilecek alan sınıfı (ör. hero beyaz alanı). */
  fieldClassName?: string
  /** Popover hizası — alan formun sağ kenarına yakınsa 'right' taşmayı önler. */
  align?: 'left' | 'right'
}

export function PeoplePicker({
  id,
  label,
  summary,
  rows,
  onRowChange,
  children,
  fieldClassName,
  align = 'left',
}: PeoplePickerProps) {
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

  return (
    <div ref={rootRef} className="relative grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <button
        type="button"
        id={id}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className={cn(
          'flex h-9 items-center justify-between gap-2 rounded-md border border-input bg-transparent px-3 text-left text-base shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring md:text-sm',
          fieldClassName,
        )}
      >
        <span className="truncate">{summary}</span>
        <ChevronDown
          aria-hidden="true"
          className={cn('h-4 w-4 shrink-0 opacity-60 transition-transform duration-300', open && 'rotate-180')}
        />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            role="dialog"
            aria-label={label}
            initial={{ opacity: 0, y: -5, scale: 0.95, filter: 'blur(10px)' }}
            animate={{ opacity: 1, y: 0, scale: 1, filter: 'blur(0px)' }}
            exit={{ opacity: 0, y: -5, scale: 0.95, filter: 'blur(10px)' }}
            transition={{ duration: 0.4, type: 'spring', bounce: 0.15 }}
            className={cn(
              // w-[min(...)]: dar ekranda sabit 18rem viewport'u taşıyordu.
              // max-h + overflow: çocuk yaşları açıldığında (6 çocuk → 3 sıra
              // select) panel ekran dışına uzamak yerine kendi içinde kayar.
              'absolute top-full z-50 mt-2 w-[min(18rem,calc(100vw-2rem))] max-h-[min(28rem,calc(100vh-8rem))] overflow-y-auto overscroll-contain rounded-xl border border-white/15 bg-brand-navy/95 p-4 shadow-[0_0_20px_rgba(0,0,0,0.35)] backdrop-blur-md',
              // Mantıksal konum: dir="rtl" altında panel karşı kenara hizalanır.
              align === 'right' ? 'end-0' : 'start-0',
            )}
          >
            <div className="flex flex-col gap-3">
              {rows.map((row) => (
                <div key={row.key} className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-white">{row.label}</p>
                    {row.hint && <p className="text-xs text-brand-ice/60">{row.hint}</p>}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      aria-label={`${row.label} sayısını azalt`}
                      disabled={row.value <= row.min}
                      onClick={() => onRowChange(row.key, row.value - 1)}
                      className="flex h-8 w-8 items-center justify-center rounded-full border border-white/20 text-white transition-colors hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent"
                    >
                      <Minus className="h-4 w-4" aria-hidden="true" />
                    </button>
                    <span className="w-5 text-center text-sm font-semibold tabular-nums text-white">
                      {row.value}
                    </span>
                    <button
                      type="button"
                      aria-label={`${row.label} sayısını artır`}
                      disabled={row.value >= row.max}
                      onClick={() => onRowChange(row.key, row.value + 1)}
                      className="flex h-8 w-8 items-center justify-center rounded-full border border-white/20 text-white transition-colors hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent"
                    >
                      <Plus className="h-4 w-4" aria-hidden="true" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default PeoplePicker
