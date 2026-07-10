import { useEffect, useState } from 'react'

/**
 * Bir değeri `delay` ms geciktirir — hızlı yazımda her tuşta değil, kullanıcı
 * duraklayınca güncellenir. Otomatik tamamlama sorgularını (her tuşta backend
 * çağrısı yerine) sınırlamak için kullanılır.
 */
export function useDebouncedValue<T>(value: T, delay = 250): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
