import { BackgroundGradientAnimation } from '@/components/ui/background-gradient-animation'
import { BRAND, rgbTriplet, shade } from '@/lib/brand'

/**
 * NightSkyBackground — AI bölgesinin (koyu "gece uçuşu" yüzeyi) sabit arka planı.
 * Login'deki blob dilinin düşük dozlu hali: hazır BackgroundGradientAnimation'ı
 * marka renkleriyle parametrize eder, blob kodunu duplike etmez.
 *
 * Dekoratif — Layout'ta tek örnek olarak, yalnızca zone === 'ai' iken render
 * edilir. `interactive={false}` bilinçli: shell arka planı sakin kalmalı
 * (login'deki fare takibi burada dikkat dağıtır, ayrıca rAF maliyeti olmaz).
 */
export function NightSkyBackground() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 opacity-40">
      <BackgroundGradientAnimation
        interactive={false}
        containerClassName="h-full w-full"
        gradientBackgroundStart={BRAND.navy}
        gradientBackgroundEnd={shade(BRAND.navy, 0.55)}
        firstColor={rgbTriplet(BRAND.blue)}
        secondColor={rgbTriplet(BRAND.iris)}
        thirdColor={rgbTriplet(BRAND.teal)}
        fourthColor={rgbTriplet(BRAND.ice)}
        fifthColor={rgbTriplet(BRAND.blue)}
        size="70%"
        blendingValue="soft-light"
      />
    </div>
  )
}

export default NightSkyBackground
