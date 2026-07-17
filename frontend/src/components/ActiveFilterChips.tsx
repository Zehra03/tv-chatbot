import { X } from 'lucide-react'
import { motion } from 'framer-motion'
import { Badge } from '@/components/ui/badge'

/**
 * Aktif filtre çipleri (PPMO B21) — sonuç listesinin üstünde, listeyi o an
 * daraltan filtreleri rozet olarak gösterir; çipteki "✕" filtreyi tek tıkla
 * temizler. Filtreler istemci tarafında uygulandığından (uiSlice → sayfanın
 * useMemo'su) çip kaldırılınca liste yeniden aranmadan kendiliğinden güncellenir.
 *
 * Salt sunum: hangi çiplerin çıkacağı ve kaldırınca ne olacağı çağıran sayfaya
 * ait (bkz. `features/ui/filterChips.ts`), böylece otel ve uçuş aynı bileşeni
 * paylaşır. Aktif filtre yokken hiç render edilmez.
 */
export interface FilterChip {
  key: string
  label: string
  onRemove: () => void
}

export function ActiveFilterChips({ chips }: { chips: FilterChip[] }) {
  if (chips.length === 0) return null

  return (
    <div role="group" aria-label="Aktif filtreler" className="flex flex-wrap items-center gap-2">
      {chips.map((chip) => (
        // Giriş animasyonu CriteriaChips deseniyle aynı. Çıkışta AnimatePresence yok: çip
        // kaldırılınca DOM'dan hemen düşer — filtre zaten kalkmışken sönümlenen bir çipin
        // ekranda kalması yanıltıcı olurdu.
        <motion.div
          key={chip.key}
          layout
          initial={{ opacity: 0, scale: 0.85 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <Badge variant="glass" className="gap-1 pr-1">
            {chip.label}
            <button
              type="button"
              aria-label={`${chip.label} filtresini kaldır`}
              onClick={chip.onRemove}
              className="rounded-full p-0.5 text-muted-foreground transition-colors hover:bg-foreground/20 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal/60"
            >
              <X className="h-3 w-3" aria-hidden />
            </button>
          </Badge>
        </motion.div>
      ))}
    </div>
  )
}
