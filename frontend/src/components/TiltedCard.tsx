import { useState } from 'react'
import type { MouseEvent, ReactNode } from 'react'
import { motion, useSpring } from 'framer-motion'
import { cn } from '@/lib/utils'

const springValues = { damping: 30, stiffness: 100, mass: 2 }

interface TiltedCardProps {
  children: ReactNode
  className?: string
  /** Maksimum eğim (derece) — geniş yüzeylerde (tablo, form) küçük tutun. */
  rotateAmplitude?: number
  /** Hover'da büyüme; tıklama hedefi kayması istenmeyen kartlarda 1 bırakın. */
  scaleOnHover?: number
}

/**
 * react-bits TiltedCard'ın içerik kartlarına uyarlanmış hali: imleç kart
 * üzerinde gezinirken yaylı (spring) 3B eğim + hafif büyüme verir. Görsel,
 * tooltip ve overlay kısımları çıkarıldı — kartın kendi cam yüzey stili
 * children'da kalır, sarmalayıcı yalnızca perspektif ve dönüşü yönetir.
 * Login vitrin kartları hariç tüm kartlarda kullanılır; dokunmatik cihazlarda
 * ve prefers-reduced-motion tercihinde eğim devre dışıdır.
 */
export function TiltedCard({
  children,
  className,
  rotateAmplitude = 8,
  scaleOnHover = 1.02,
}: TiltedCardProps) {
  const rotateX = useSpring(0, springValues)
  const rotateY = useSpring(0, springValues)
  const scale = useSpring(1, springValues)

  const [enabled] = useState(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true
    return (
      window.matchMedia('(hover: hover)').matches &&
      !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    )
  })

  function handleMouseMove(e: MouseEvent<HTMLDivElement>) {
    if (!enabled) return
    const rect = e.currentTarget.getBoundingClientRect()
    const offsetX = e.clientX - rect.left - rect.width / 2
    const offsetY = e.clientY - rect.top - rect.height / 2
    rotateX.set((offsetY / (rect.height / 2)) * -rotateAmplitude)
    rotateY.set((offsetX / (rect.width / 2)) * rotateAmplitude)
  }

  function handleMouseEnter() {
    if (!enabled) return
    scale.set(scaleOnHover)
  }

  function handleMouseLeave() {
    scale.set(1)
    rotateX.set(0)
    rotateY.set(0)
  }

  return (
    // Eğimli kart komşularının üstüne taşabilir — hover'da öne al.
    <div
      className={cn('relative [perspective:800px] hover:z-10', className)}
      onMouseMove={handleMouseMove}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <motion.div
        className="h-full"
        style={{ rotateX, rotateY, scale, transformStyle: 'preserve-3d' }}
      >
        {children}
      </motion.div>
    </div>
  )
}
