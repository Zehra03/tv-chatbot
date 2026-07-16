import { BRAND, rgbTriplet, shade } from '@/lib/brand'

/**
 * NightSkyBackground — AI bölgesinin (koyu "gece uçuşu" yüzeyi) sabit arka planı.
 *
 * ÖNCEDEN: Aceternity `BackgroundGradientAnimation` — 5 büyük radial blob,
 * `mix-blend-mode` + SVG gooey filtresi (`url(#blurMe)`) + 40px blur, hepsi
 * sonsuz CSS animasyonuyla. `fixed` olarak her AI sayfasının arkasında durup
 * scroll'da tüm viewport'u her karede yeniden rasterize ediyordu; üstteki
 * `backdrop-blur` header ile birleşince "scroll'da ekran geç geliyor"un ana
 * sebebiydi.
 *
 * ŞİMDİ: aynı yumuşak gece-uçuşu görünümünü veren STATİK, çok katmanlı radial
 * gradyan. Tek boyama — animasyon, SVG filtre, blend-mode, blur YOK. Renkler
 * yine `lib/brand`'den türetilir (ham hex yazılmaz). Dekoratif; yalnızca
 * Layout'ta zone === 'ai' iken tek örnek olarak render edilir.
 */
const NIGHT_SKY = [
  `radial-gradient(50% 42% at 18% 18%, rgba(${rgbTriplet(BRAND.blue)}, 0.22), transparent 70%)`,
  `radial-gradient(46% 40% at 82% 24%, rgba(${rgbTriplet(BRAND.iris)}, 0.18), transparent 70%)`,
  `radial-gradient(55% 46% at 74% 82%, rgba(${rgbTriplet(BRAND.teal)}, 0.15), transparent 72%)`,
  `radial-gradient(42% 40% at 26% 86%, rgba(${rgbTriplet(BRAND.ice)}, 0.10), transparent 72%)`,
  // Taban (navy) EN ALTTA olmalı: CSS `background` kısayolunda ilk katman üstte.
  `linear-gradient(160deg, ${shade(BRAND.navy, 1)}, ${shade(BRAND.navy, 0.5)})`,
].join(', ')

export function NightSkyBackground() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-0"
      style={{ background: NIGHT_SKY }}
    />
  )
}

export default NightSkyBackground
