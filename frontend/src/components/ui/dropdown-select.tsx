import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Gece uçuşu yüzeyi için özel select — tasarım referansı:
 * https://21st.dev/@chetanverma16/components/dropdown-menu (rounded-xl cam
 * panel, dönen chevron, blur'dan belirginleşen kademeli item animasyonu);
 * renkler marka temasına uyarlanmıştır. Native <select> yerine listbox deseni:
 * tetikleyici buton aria-haspopup/expanded taşır, seçenekler role="option".
 * Kontrollü rezervasyon formu (açık bölge) native select ile kalır.
 */
export interface DropdownSelectOption {
  value: string
  label: string
}

interface DropdownSelectProps {
  value: string
  options: DropdownSelectOption[]
  onChange: (value: string) => void
  /** <Label htmlFor> ilişkisi için tetikleyici buton id'si. */
  id?: string
  'aria-label'?: string
  /** Tetikleyiciye ek sınıf (genişlik vb.). */
  className?: string
}

export function DropdownSelect({
  value,
  options,
  onChange,
  id,
  'aria-label': ariaLabel,
  className,
}: DropdownSelectProps) {
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

  const current = options.find((o) => o.value === value) ?? options[0]

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        id={id}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        // METİN RENGİ BİLEREK YOK — miras alınır (ui/label ile aynı desen). Bu
        // bileşen iki farklı yüzeyde yaşıyor: normal sayfalarda Layout'un
        // `text-foreground`'unu, hero'da SearchHero'nun `text-white`'ını alır.
        // Sabit `text-foreground` yazılsaydı hero'nun lacivert örtüsünde açık
        // temada siyaha dönerdi. Zemin/kenar token'lı kalır (sayfa yüzeyi için
        // doğru); hero örneği bunları className ile ezer — heroFieldClass deseni.
        className={cn(
          'flex h-9 items-center justify-between gap-2 rounded-xl border border-border bg-card px-3 text-sm shadow-soft transition-colors hover:border-primary/60 hover:bg-muted focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
          className,
        )}
      >
        <span className="truncate">{current?.label}</span>
        <motion.span
          aria-hidden
          animate={{ rotate: open ? 180 : 0 }}
          transition={{ duration: 0.4, ease: 'easeInOut', type: 'spring' }}
          // Renk değil opaklık: miras alınan metin rengini takip eder, yani her
          // yüzeyde (koyu hero / açık sayfa) doğru tonda kalır.
          className="shrink-0 opacity-60"
        >
          <ChevronDown className="h-4 w-4" />
        </motion.span>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            role="listbox"
            aria-label={ariaLabel}
            initial={{ opacity: 0, y: -5, scale: 0.95, filter: 'blur(10px)' }}
            animate={{ opacity: 1, y: 0, scale: 1, filter: 'blur(0px)' }}
            exit={{ opacity: 0, y: -5, scale: 0.95, filter: 'blur(10px)' }}
            transition={{ duration: 0.4, type: 'spring', bounce: 0.15 }}
            className="pax-popover absolute left-0 top-full z-50 mt-2 flex w-max min-w-full max-w-[18rem] flex-col gap-0.5 rounded-xl p-1"
          >
            {options.map((option, index) => {
              const selected = option.value === value
              return (
                <motion.button
                  key={option.value || '__empty'}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  initial={{ opacity: 0, x: 10, scale: 0.95, filter: 'blur(10px)' }}
                  animate={{ opacity: 1, x: 0, scale: 1, filter: 'blur(0px)' }}
                  transition={{ duration: 0.25, ease: 'easeOut', delay: index * 0.05 }}
                  whileTap={{ scale: 0.97 }}
                  onClick={() => {
                    onChange(option.value)
                    setOpen(false)
                  }}
                  className={cn(
                    'flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition-colors',
                    selected
                      ? 'bg-muted text-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-foreground',
                  )}
                >
                  <span className="truncate">{option.label}</span>
                  {selected && <Check className="h-4 w-4 shrink-0 text-primary" aria-hidden />}
                </motion.button>
              )
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default DropdownSelect
