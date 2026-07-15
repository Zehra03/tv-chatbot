import { useEffect, useState } from 'react'

/**
 * `active` kesintisiz `delay` ms boyunca true kalırsa true'ya döner; `active`
 * false olunca anında sıfırlanır (PPMO K24).
 *
 * Amaç: kısa süren işlemleri (hızlı slot-filling yanıtı) tetiklemeden, yalnız
 * gerçekten uzayan işlemler için ikincil bir "sürüyor" göstergesi açmak. TourVisio
 * araması saniyeler sürerken kullanıcı sohbette "Arıyorum…" görsün; anlık bir
 * takip sorusu bu eşiği aşmadan yanıtlanır ve gösterge sade kalır.
 */
export function useDelayedFlag(active: boolean, delay: number): boolean {
  const [elapsed, setElapsed] = useState(false)

  useEffect(() => {
    if (!active) {
      setElapsed(false)
      return
    }
    const timer = setTimeout(() => setElapsed(true), delay)
    return () => clearTimeout(timer)
  }, [active, delay])

  // active kapanır kapanmaz (effect henüz koşmadan) da false görünmeli.
  return active && elapsed
}
