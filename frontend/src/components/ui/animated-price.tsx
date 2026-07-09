import { useEffect, useState } from 'react'
import NumberFlow from '@number-flow/react'
import { formatPrice } from '@/utils/format'

/**
 * Animasyonlu fiyat — girişte 0'dan gerçek tutara sayar (NumberFlow;
 * prefers-reduced-motion'a kendisi saygı duyar).
 *
 * Erişilebilirlik/test sözleşmesi: tam biçimli fiyat (formatPrice) sr-only
 * span'de tek metin düğümü olarak durur — ekran okuyucular temiz bir değer
 * duyar, testlerin `getByText(/1\.200/)` sorguları NumberFlow'un rakamları
 * nasıl böldüğünden bağımsız eşleşir. Görsel kopya aria-hidden'dır.
 */
interface AnimatedPriceProps {
  amount: number
  currency: string
  className?: string
}

/** jsdom, NumberFlow'un custom element yaşam döngüsünü desteklemiyor
 * (willUpdate hatası) — testlerde düz biçimli fiyata düşülür. */
const supportsNumberFlow =
  typeof navigator === 'undefined' || !/jsdom/i.test(navigator.userAgent)

export function AnimatedPrice({ amount, currency, className }: AnimatedPriceProps) {
  const [value, setValue] = useState(0)
  useEffect(() => {
    setValue(amount)
  }, [amount])

  if (!supportsNumberFlow) {
    return <span className={className}>{formatPrice(amount, currency)}</span>
  }

  return (
    <span className={className}>
      <span className="sr-only">{formatPrice(amount, currency)}</span>
      <span aria-hidden="true">
        <NumberFlow
          value={value}
          locales="tr-TR"
          format={{ style: 'currency', currency, maximumFractionDigits: 0 }}
        />
      </span>
    </span>
  )
}

export default AnimatedPrice
