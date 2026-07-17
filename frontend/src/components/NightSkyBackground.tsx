import { useTheme } from '@/app/theme'
import { BRAND, rgbTriplet, shade, tint } from '@/lib/brand'

/**
 * SkyBackground — uygulamanın sabit arka planı; temaya göre gece ya da gündüz göğü.
 *
 * ÖNCEDEN: Aceternity `BackgroundGradientAnimation` — 5 büyük radial blob,
 * `mix-blend-mode` + SVG gooey filtresi (`url(#blurMe)`) + 40px blur, hepsi
 * sonsuz CSS animasyonuyla. `fixed` olarak her sayfanın arkasında durup
 * scroll'da tüm viewport'u her karede yeniden rasterize ediyordu; üstteki
 * `backdrop-blur` header ile birleşince "scroll'da ekran geç geliyor"un ana
 * sebebiydi.
 *
 * ŞİMDİ: aynı yumuşak görünümü veren STATİK, çok katmanlı radial gradyan. Tek
 * boyama — animasyon, SVG filtre, blend-mode, blur YOK. Renkler yine
 * `lib/brand`'den türetilir (ham hex yazılmaz). Dekoratif; Layout'ta tek örnek.
 *
 * GÜNDÜZ GÖĞÜ neden var: açık temada zemin saf beyaz olsaydı, cam yüzeyler
 * (`.glass-card` = beyaz/0.65) beyaz üstünde beyaz kalır, yani görünmezdi.
 * Gündüz göğü camın oturacağı hafif renkli zemini verir — gecenin aynadaki
 * karşılığı: aynı blob konumları, düşük doygunluk, beyaza yakın taban.
 */
const NIGHT_SKY = [
  `radial-gradient(50% 42% at 18% 18%, rgba(${rgbTriplet(BRAND.blue)}, 0.22), transparent 70%)`,
  `radial-gradient(46% 40% at 82% 24%, rgba(${rgbTriplet(BRAND.iris)}, 0.18), transparent 70%)`,
  `radial-gradient(55% 46% at 74% 82%, rgba(${rgbTriplet(BRAND.teal)}, 0.15), transparent 72%)`,
  `radial-gradient(42% 40% at 26% 86%, rgba(${rgbTriplet(BRAND.ice)}, 0.10), transparent 72%)`,
  // Taban (navy) EN ALTTA olmalı: CSS `background` kısayolunda ilk katman üstte.
  `linear-gradient(160deg, ${shade(BRAND.navy, 1)}, ${shade(BRAND.navy, 0.5)})`,
].join(', ')

const DAY_SKY = [
  `radial-gradient(50% 42% at 18% 18%, rgba(${rgbTriplet(BRAND.blue)}, 0.13), transparent 70%)`,
  `radial-gradient(46% 40% at 82% 24%, rgba(${rgbTriplet(BRAND.iris)}, 0.11), transparent 70%)`,
  `radial-gradient(55% 46% at 74% 82%, rgba(${rgbTriplet(BRAND.teal)}, 0.10), transparent 72%)`,
  `radial-gradient(42% 40% at 26% 86%, rgba(${rgbTriplet(BRAND.ice)}, 0.16), transparent 72%)`,
  // Taban: buzdan beyaza — camın okunması için yeterli, metni yormayacak kadar soluk.
  `linear-gradient(160deg, ${tint(BRAND.ice, 0.55)}, ${tint(BRAND.blue, 0.93)})`,
].join(', ')

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
