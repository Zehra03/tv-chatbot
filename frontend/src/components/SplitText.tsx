import { type CSSProperties } from 'react'
import { motion, useReducedMotion } from 'framer-motion'

/**
 * SplitText — metni tek blok olarak yumuşak bir fade + hafif yukarı kayma ile
 * getirir. Eskiden GSAP + react-bits ile harf harf bölünüyordu; sürekli donma/
 * kasma yaptığı için GSAP (gsap, @gsap/react) kaldırıldı ve zaten projede olan
 * framer-motion ile hafif bir giriş bırakıldı.
 *
 * Prop arayüzü GERİYE DÖNÜK uyumlu tutuldu (çağrı yerleri değişmesin). GSAP'e
 * özgü proplar (ease/splitType/from/to/threshold/rootMargin/force3D) kabul
 * edilir ama artık YOK SAYILIR. Tek blok render edildiği için RTL `getByText`
 * artık tam dizeyi bulur (eski harf-span uyarısı geçersiz).
 */
interface SplitTextProps {
  text: string
  className?: string
  /** Giriş gecikmesi (ms) — eskiden harf stagger'ıydı, artık blok gecikmesi. */
  delay?: number
  /** Animasyon süresi (saniye). */
  duration?: number
  tag?: 'p' | 'h1' | 'h2' | 'h3' | 'span' | 'div'
  textAlign?: CSSProperties['textAlign']
  onLetterAnimationComplete?: () => void
  /** Eski API ile uyum için kabul edilir, kullanılmaz. */
  ease?: string
  splitType?: 'chars' | 'words' | 'lines' | 'words, chars'
  from?: unknown
  to?: unknown
  threshold?: number
  rootMargin?: string
  force3D?: boolean
}

export function SplitText({
  text,
  className = '',
  delay = 0,
  duration = 0.6,
  tag = 'p',
  textAlign = 'center',
  onLetterAnimationComplete,
}: SplitTextProps) {
  const reduced = useReducedMotion()
  // Union tag'i tek bir motion bileşenine daraltıyoruz; tüm HTML tag'leri aynı
  // temel motion prop setini (initial/animate/transition) kabul eder.
  const MotionTag = motion[tag] as typeof motion.p

  return (
    <MotionTag
      className={`split-parent ${className}`}
      style={{ textAlign }}
      initial={reduced ? false : { opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration, delay: delay / 1000, ease: 'easeOut' }}
      onAnimationComplete={onLetterAnimationComplete}
    >
      {text}
    </MotionTag>
  )
}

export default SplitText
