import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, Search } from 'lucide-react'
import { cn } from '@/lib/utils'
import { COUNTRIES, findCountry } from '@/lib/countries'

/**
 * Aranabilir ülke seçici (combobox deseni). DropdownSelect ~2 seçenek için yeterliydi; ülke listesi
 * 200+ olduğundan arama kutusu ve klavye gezinmesi şart. Görünüm gece uçuşu yüzeyiyle aynı reçeteyi
 * (cam panel, dönen chevron) izler.
 *
 * Değer ISO-3166 alpha-2 kodudur — arama kodu paylaşıldığı için (+1 ABD/Kanada, +7 Rusya/Kazakistan)
 * telefon alanında da ülke kodu değil ÜLKE saklanır.
 */
interface CountrySelectProps {
  /** Seçili ülkenin ISO alpha-2 kodu; boş dize = seçim yok. */
  value: string
  onChange: (code: string) => void
  /** <Label htmlFor> ilişkisi için tetikleyici buton id'si. */
  id?: string
  'aria-label'?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
  /** 'name': "Türkiye" · 'dial': "TR +90" — telefon alanının dar tetikleyicisi. */
  variant?: 'name' | 'dial'
  placeholder?: string
  className?: string
  onBlur?: () => void
}

/** NFD ayrıştırmasından artakalan birleşik aksan işaretleri (U+0300–U+036F). */
const COMBINING_MARKS = /[̀-ͯ]/g

/** Türkçe arama: büyük/küçük ve aksan farkını yok sayar ('İSTANBUL' ≈ 'istanbul'). */
function foldTr(value: string): string {
  return value.toLocaleLowerCase('tr').normalize('NFD').replace(COMBINING_MARKS, '')
}

export function CountrySelect({
  value,
  onChange,
  id,
  'aria-label': ariaLabel,
  'aria-invalid': ariaInvalid,
  'aria-describedby': ariaDescribedBy,
  variant = 'name',
  placeholder = 'Ülke seçin',
  className,
  onBlur,
}: CountrySelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const listboxId = useId()

  const selected = findCountry(value)

  const matches = useMemo(() => {
    const q = foldTr(query.trim())
    if (!q) return COUNTRIES
    const digits = q.replace(/[^\d]/g, '')
    return COUNTRIES.filter(
      (c) =>
        foldTr(c.name).includes(q) ||
        foldTr(c.code) === q ||
        (digits.length > 0 && c.dial.startsWith(digits)),
    )
  }, [query])

  // Açılışta arama kutusuna odaklan ve seçili ülkeden başla; kapanışta aramayı sıfırla.
  useEffect(() => {
    if (!open) {
      setQuery('')
      return
    }
    const current = matches.findIndex((c) => c.code === value)
    setActiveIndex(current >= 0 ? current : 0)
    searchRef.current?.focus()
    // matches kasıtlı olarak dışarıda: yalnız açılış anındaki konum önemli.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  // Arama değişince aktif satır listenin başına döner (eski indeks filtrelenmiş listede anlamsız).
  useEffect(() => setActiveIndex(0), [query])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false)
        onBlur?.()
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open, onBlur])

  // Klavyeyle gezinirken aktif satırı görünür tut (liste kaydırmalı).
  // scrollIntoView jsdom'da yok — süsleme olduğu için varlığını kontrol edip geçiyoruz.
  useEffect(() => {
    if (!open) return
    const active = listRef.current?.querySelector('[data-active="true"]')
    if (active instanceof HTMLElement && typeof active.scrollIntoView === 'function') {
      active.scrollIntoView({ block: 'nearest' })
    }
  }, [open, activeIndex])

  const commit = (code: string) => {
    onChange(code)
    setOpen(false)
    onBlur?.()
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setOpen(false)
      onBlur?.()
      return
    }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      if (!open) {
        setOpen(true)
        return
      }
      const delta = e.key === 'ArrowDown' ? 1 : -1
      setActiveIndex((i) => {
        if (matches.length === 0) return 0
        return (i + delta + matches.length) % matches.length
      })
      return
    }
    if (e.key === 'Enter' && open) {
      e.preventDefault()
      const pick = matches[activeIndex]
      if (pick) commit(pick.code)
    }
  }

  const triggerLabel = selected
    ? variant === 'dial'
      ? `${selected.code} +${selected.dial}`
      : selected.name
    : placeholder

  return (
    <div ref={rootRef} className="relative" onKeyDown={onKeyDown}>
      <button
        type="button"
        id={id}
        role="combobox"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listboxId : undefined}
        aria-invalid={ariaInvalid}
        aria-describedby={ariaDescribedBy}
        onClick={() => setOpen((o) => !o)}
        className={cn(
          'flex h-9 w-full items-center justify-between gap-2 rounded-xl border border-white/15 bg-white/5 px-3 text-sm text-white shadow-[0_0_20px_rgba(0,0,0,0.2)] backdrop-blur-sm transition-colors hover:border-brand-teal/60 hover:bg-white/10 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-brand-teal aria-[invalid=true]:border-red-400/70',
          !selected && 'text-white/40',
          className,
        )}
      >
        <span className="truncate">{triggerLabel}</span>
        <motion.span
          aria-hidden
          animate={{ rotate: open ? 180 : 0 }}
          transition={{ duration: 0.4, ease: 'easeInOut', type: 'spring' }}
          className="shrink-0 text-brand-ice/70"
        >
          <ChevronDown className="h-4 w-4" />
        </motion.span>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -5, scale: 0.95, filter: 'blur(10px)' }}
            animate={{ opacity: 1, y: 0, scale: 1, filter: 'blur(0px)' }}
            exit={{ opacity: 0, y: -5, scale: 0.95, filter: 'blur(10px)' }}
            transition={{ duration: 0.25, type: 'spring', bounce: 0.15 }}
            className="pax-popover absolute left-0 top-full z-50 mt-2 w-max min-w-full max-w-[20rem] rounded-xl p-1"
          >
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-brand-ice/50"
                aria-hidden
              />
              <input
                ref={searchRef}
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Ülke ara"
                aria-controls={listboxId}
                placeholder="Ülke ya da kod ara…"
                className="mb-1 w-full rounded-lg border border-white/10 bg-white/5 py-2 pl-8 pr-2.5 text-sm text-white placeholder:text-white/40 focus:border-brand-teal/60 focus:outline-none"
              />
            </div>

            <div
              ref={listRef}
              id={listboxId}
              role="listbox"
              aria-label={ariaLabel ?? 'Ülke listesi'}
              className="flex max-h-56 flex-col gap-0.5 overflow-y-auto"
            >
              {matches.length === 0 ? (
                <p className="px-2.5 py-3 text-sm text-brand-ice/60">Ülke bulunamadı.</p>
              ) : (
                matches.map((country, index) => {
                  const isSelected = country.code === value
                  return (
                    <button
                      key={country.code}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      data-active={index === activeIndex}
                      onMouseEnter={() => setActiveIndex(index)}
                      onClick={() => commit(country.code)}
                      className={cn(
                        'flex w-full shrink-0 items-center justify-between gap-3 rounded-lg px-2.5 py-2 text-left text-sm transition-colors',
                        index === activeIndex
                          ? 'bg-white/10 text-white'
                          : 'text-brand-ice/80 hover:text-white',
                      )}
                    >
                      <span className="truncate">{country.name}</span>
                      <span className="flex shrink-0 items-center gap-2 text-xs text-brand-ice/60">
                        {variant === 'dial' ? `+${country.dial}` : country.code}
                        {isSelected && <Check className="h-4 w-4 text-brand-teal" aria-hidden />}
                      </span>
                    </button>
                  )
                })
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default CountrySelect
