import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { motion } from 'framer-motion'
import { Hotel, Plane, X } from 'lucide-react'
import { ResultCards } from '@/features/chat/ResultCards'
import { cn } from '@/lib/utils'
import type { PartialCriteria, ResultCard } from '@/types'

// overflow-x-hidden ŞART: yalnız overflow-y-auto verilince CSS overflow-x'i de
// auto'ya çeker; dar panelde bir kart taşarsa yatay kaydırma çubuğu belirir.
// Yatay taşmayı kırparak paneli sadece dikey kaydırılır tutuyoruz.
const scrollClass =
  'flex-1 overflow-y-auto overflow-x-hidden p-3 [scrollbar-color:theme(colors.white/25%)_transparent] [scrollbar-width:thin]'

/** Panel başlığı — sonuç türü (otel/uçuş) + adet; mobil drawer'da kapat düğmesi. */
function ResultsHeader({ cards, onClose }: { cards: ResultCard[]; onClose?: () => void }) {
  const type = cards[0]?.productType
  const label = type === 'flight' ? 'Uçuşlar' : 'Oteller'
  const Icon = type === 'flight' ? Plane : Hotel
  return (
    <div className="flex shrink-0 items-center justify-between gap-2 border-b border-white/10 px-4 py-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-white">
        <Icon className="h-4 w-4 text-brand-teal" aria-hidden />
        {label}
        <span className="rounded-full bg-white/10 px-1.5 py-0.5 text-[11px] font-medium tabular-nums text-brand-ice/70">
          {cards.length}
        </span>
      </h2>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Sonuçları kapat"
          className="rounded-lg p-1.5 text-brand-ice/60 transition-colors hover:bg-white/10 hover:text-white"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      )}
    </div>
  )
}

/**
 * Sağa yaslı sonuç ekranı (madde 8) — masaüstünde (lg+) sohbetin yanında sabit
 * durur; sohbet sola daralır, kartlar burada kompakt listede görünür. Her yeni
 * aramada ChatPage en son sonuç kümesini buraya verir.
 */
export function ResultsPanel({
  cards,
  criteria,
  className,
}: {
  cards: ResultCard[]
  criteria?: PartialCriteria
  className?: string
}) {
  return (
    <motion.aside
      aria-label="Sonuç paneli"
      className={cn('glass-card flex min-h-0 flex-col overflow-hidden', className)}
      initial={{ opacity: 0, x: 24 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
    >
      <ResultsHeader cards={cards} />
      <div className={scrollClass}>
        <ResultCards cards={cards} criteria={criteria} />
      </div>
    </motion.aside>
  )
}

/**
 * Mobil sonuç ekranı — dar ekranda yan yana yer olmadığından sağdan kayan
 * kapanabilir bir katman (madde 8: "mobilde kapanabilecek"). Sohbetteki
 * "Sonuçları göster" düğmesiyle açılır; overlay/Escape/kapat ile kapanır.
 * lg+'da hiç görünmez (docked panel devrededir).
 */
export function ResultsDrawer({
  open,
  onClose,
  cards,
  criteria,
}: {
  open: boolean
  onClose: () => void
  cards: ResultCard[]
  criteria?: PartialCriteria
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open || cards.length === 0) return null

  return createPortal(
    <div className="fixed inset-0 z-50 lg:hidden">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden />
      <motion.aside
        role="dialog"
        aria-modal="true"
        aria-label="Sonuç paneli"
        className="glass-card absolute inset-y-0 right-0 flex w-full max-w-md flex-col rounded-none border-y-0 border-r-0"
        initial={{ x: '100%' }}
        animate={{ x: 0 }}
        transition={{ duration: 0.28, ease: 'easeOut' }}
      >
        <ResultsHeader cards={cards} onClose={onClose} />
        <div className={scrollClass}>
          <ResultCards cards={cards} criteria={criteria} />
        </div>
      </motion.aside>
    </div>,
    document.body,
  )
}
