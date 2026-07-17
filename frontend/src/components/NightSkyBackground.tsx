import { useTheme } from '@/app/theme'
import { BRAND, shade, tint } from '@/lib/brand'

/**
 * SkyBackground — uygulamanın sabit arka planı; temaya göre çok soluk bir zemin.
 *
 * DÜZ (flat) tasarım: kartlar artık DOLU yüzey (bg-card + kenarlık + yumuşak gölge),
 * yani okunmak için renkli zemine ihtiyaçları yok. Bu yüzden AÇIK tema zemini neredeyse
 * saf beyaz — brief'in "büyük dekoratif gradyanlardan kaçın" kuralına uyar; yalnız üstte
 * fark edilir edilmez bir mavi ferahlık var. KOYU tema demote edildi ama düz kalmasın diye
 * hafif bir navy gradyan korunur. Renkler `lib/brand`'den türetilir (ham hex yok).
 */
const NIGHT_SKY = `linear-gradient(180deg, ${shade(BRAND.navy, 1)}, ${shade(BRAND.navy, 0.7)})`

// Açık tema: beyaza en ince mavi ferahlık — büyük dekoratif blob yok.
const DAY_SKY = `linear-gradient(180deg, ${tint(BRAND.blue, 0.985)}, #ffffff 55%)`

export function SkyBackground() {
  const { resolvedTheme } = useTheme()
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-0"
      style={{ background: resolvedTheme === 'dark' ? NIGHT_SKY : DAY_SKY }}
    />
  )
}

/** @deprecated `SkyBackground` kullanın — arka plan artık iki temayı da kapsıyor. */
export const NightSkyBackground = SkyBackground

export default SkyBackground
