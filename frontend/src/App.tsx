import { useEffect, useState } from 'react'
import Design from '@/pages/Design'
import LoginPage from '@/features/auth/LoginPage'

/**
 * Geçici önizleme yönlendirmesi — React Router ileride eklenecek.
 *   localhost:5173/#login  -> Login ekranı
 *   localhost:5173/        -> Tasarım playground
 */
function App() {
  const [hash, setHash] = useState(() => window.location.hash)

  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash)
    window.addEventListener('hashchange', onHashChange)
    // Temizlik: bileşen kaldırılınca dinleyiciyi çıkar (memory leak önlenir).
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  return hash === '#login' ? <LoginPage /> : <Design />
}

export default App
