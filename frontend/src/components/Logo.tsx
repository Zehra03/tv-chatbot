import { cn } from '@/lib/utils'

interface LogoProps {
  /** Yükseklik (px); en-boy oranı korunur. */
  height?: number
  className?: string
}

/**
 * PaxAssist logosu — resmî görseli (public/logo.png) OLDUĞU GİBİ kullanır.
 * Görsel değiştirilmez/yeniden çizilmez; yalnızca yüksekliği ayarlanır.
 */
export function Logo({ height = 48, className }: LogoProps) {
  return (
    <img
      src="/logo.png"
      alt="PaxAssist — AI Travel Assistant"
      style={{ height, width: 'auto' }}
      className={cn('select-none', className)}
    />
  )
}

export default Logo
