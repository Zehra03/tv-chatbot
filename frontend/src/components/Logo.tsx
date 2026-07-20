import { cn } from '@/lib/utils'

interface LogoProps {
  /** Yükseklik (px); en-boy oranı korunur. */
  height?: number
  /**
   * Hangi varyant basılacak. 'auto' (varsayılan) `.dark` sınıfına göre CSS ile
   * seçer — bunun için ikisini de basıp biriyle gizler, çünkü tema <html>'de ve
   * React context'i her zaman doğruyu söylemez (bkz. Design.tsx'in `theme-light` /
   * `dark` sarmalları: CSS kalıtımı context'e görünmez).
   * Her zaman koyu zeminde duran ekranlar (LandingPage) 'dark' geçmeli.
   */
  variant?: 'auto' | 'light' | 'dark'
  className?: string
}

const LIGHT_SRC = '/logo-wordmark.png'
const DARK_SRC = '/logo-wordmark-dark.png'
const ALT = 'PaxAssist — AI Travel Assistant'

/**
 * PaxAssist logosu — yalnızca wordmark ("PaxAssist" + AI TRAVEL ASSISTANT).
 *
 * public/logo-wordmark.png, logo.png'nin alt yazı bloğuna kırpılmış hâli: camsı/
 * parlayan "P" konuşma balonu ikonu tasarımı yorduğu için çıkarıldı. Kırpma
 * logo.png'nin alfa bantlarından türetildi (ikon y=515–2608 atıldı, yazı
 * y=2696–3388 tutuldu), yani harfler yeniden çizilmedi — orijinal varlık.
 *
 * -dark varyantında lacivert aile (koyu "Pax" + arduvaz tagline) beyaza çevrildi;
 * teal "Assist", turuncu nokta ve ayırıcı çizgiler korundu. Bu varyant sayesinde
 * koyu zeminde ESKİDEN GEREKEN beyaz halo kalktı: halo lacivert harfleri okunur
 * kılmak içindi, dekoratif değildi — kaynağı düzeltince ihtiyaç de bitti.
 *
 * DİKKAT: oran artık 1:1 değil ~4:1. `height` tüm kareyi değil doğrudan yazının
 * yüksekliğini verir, o yüzden çağrı yerlerindeki sayılar buna göre küçültüldü.
 */
export function Logo({ height = 32, variant = 'auto', className }: LogoProps) {
  if (variant !== 'auto') {
    return (
      <img
        src={variant === 'dark' ? DARK_SRC : LIGHT_SRC}
        alt={ALT}
        style={{ height, width: 'auto' }}
        className={cn('select-none', className)}
      />
    )
  }

  return (
    <>
      <img
        src={LIGHT_SRC}
        alt={ALT}
        style={{ height, width: 'auto' }}
        className={cn('select-none dark:hidden', className)}
      />
      <img
        src={DARK_SRC}
        alt=""
        aria-hidden="true"
        style={{ height, width: 'auto' }}
        className={cn('hidden select-none dark:block', className)}
      />
    </>
  )
}

export default Logo
