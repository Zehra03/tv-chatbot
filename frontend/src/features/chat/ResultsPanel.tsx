import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { motion } from 'framer-motion'
import { Hotel, Plane, X } from 'lucide-react'
import { ResultCards } from '@/features/chat/ResultCards'
import { LoadingState } from '@/components/LoadingState'
import { cn } from '@/lib/utils'
import type { PartialCriteria, ResultCard } from '@/types'

// overflow-x-hidden ŞART: yalnız overflow-y-auto verilince CSS overflow-x'i de
// auto'ya çeker; dar panelde bir kart taşarsa yatay kaydırma çubuğu belirir.
// Yatay taşmayı kırparak paneli sadece dikey kaydırılır tutuyoruz.
// results-scroll: index.css bu kap İÇİNDEki iç içe backdrop-filter'ları kapatır
// (panelin kendi camı yeter) — yoksa kaydırırken Chromium dikişte piksel bozar.
const scrollClass =
  'results-scroll flex-1 overflow-y-auto overflow-x-hidden p-3 [scrollbar-color:theme(colors.white/25%)_transparent] [scrollbar-width:thin]'

/**
 * Panel gövdesi — kaydırılabilir kart listesi. Yeni bir arama sürerken (`loading`)
 * üstte "Aranıyor…" spinner'ı belirir ve mevcut kartlar soluklaşır: panel kaybolup
 * yeniden gelmeden, sonuçların tazelendiği görünür (PPMO K24). İlk aramada panel
 * henüz yoktur — o an geri bildirimi sohbetteki "Arıyorum…" göstergesi verir.
 */
function ResultsBody({
  cards,
  criteria,
  loading,
}: {
  cards: ResultCard[]
  criteria?: PartialCriteria
  loading?: boolean
}) {
  return (
    <div className={scrollClass}>
      {loading && <LoadingState label="Aranıyor…" className="mb-3 text-muted-foreground" />}
      <div className={cn('transition-opacity duration-200', loading && 'opacity-50')}>
        <ResultCards cards={cards} criteria={criteria} />
      </div>
    </div>
  )
}

/** Panel başlığı — sonuç türü (otel/uçuş) + adet; mobil drawer'da kapat düğmesi. */
function ResultsHeader({ cards, onClose }: { cards: ResultCard[]; onClose?: () => void }) {
  const type = cards[0]?.productType
  const label = type === 'flight' ? 'Uçuşlar' : 'Oteller'
  const Icon = type === 'flight' ? Plane : Hotel
  return (
    <div className="flex shrink-0 items-center justify-between gap-2 border-b border-foreground/10 px-4 py-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Icon className="h-4 w-4 text-brand-teal" aria-hidden />
        {label}
        <span className="rounded-full bg-foreground/10 px-1.5 py-0.5 text-[11px] font-medium tabular-nums text-muted-foreground">
          {cards.length}
        </span>
      </h2>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          aria-label="Sonuçları kapat"
          className="rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-foreground/10 hover:text-foreground"
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
  loading,
}: {
  cards: ResultCard[]
  criteria?: PartialCriteria
  className?: string
  /** Görünen panel üzerinde yeni bir arama sürüyor — üstte spinner, kartlar solar. */
  loading?: boolean
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
      <ResultsBody cards={cards} criteria={criteria} loading={loading} />
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
  loading,
}: {
  open: boolean
  onClose: () => void
  cards: ResultCard[]
  criteria?: PartialCriteria
  /** Açık drawer üzerinde yeni bir arama sürüyor — üstte spinner, kartlar solar. */
  loading?: boolean
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
        <ResultsBody cards={cards} criteria={criteria} loading={loading} />
      </motion.aside>
    </div>,
    document.body,
  )
}
