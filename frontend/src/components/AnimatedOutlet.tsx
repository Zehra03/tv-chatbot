import { useState } from 'react'
import { useOutlet } from 'react-router-dom'

/**
 * createBrowserRouter ile çıkış animasyonu için "dondurulmuş outlet" deseni:
 * exit animasyonu sırasında useOutlet() çoktan YENİ rotanın elemanını
 * döndürdüğünden <Outlet /> doğrudan animate edilemez (ayrılan ekran yeni
 * içerikle titrer). Mount anındaki outlet'i state'e sabitleriz —
 * AnimatePresence içindeki her motion.div (key=pathname) kendi kopyasıyla
 * mount olur, ayrılan kopya eski sayfayı göstermeye devam eder.
 */
export function AnimatedOutlet() {
  const outlet = useOutlet()
  const [frozen] = useState(outlet)
  return <>{frozen}</>
}

export default AnimatedOutlet
