import { useEffect, useState } from 'react'

/**
 * İstemci tarafı "Daha fazla göster" — bir listenin ilk `step` öğesini gösterir,
 * her tıkta `step` kadar daha açar (docs/frontend-architecture.md §8, madde 9).
 * Sonuçların TAMAMI zaten elde; amaç ilk boyama yükünü azaltmak, sunucu
 * sayfalama DEĞİL. Liste referansı değişince (yeni arama / filtre) baştan
 * başlar ki kullanıcı her zaman ilk sayfayı görsün.
 */
export function useProgressiveReveal<T>(items: T[], step = 5) {
  const [count, setCount] = useState(step)

  // Yeni küme → ilk sayfaya dön. items referansı arama/filtre değişince yenilenir.
  useEffect(() => {
    setCount(step)
  }, [items, step])

  const visible = items.slice(0, count)
  const hasMore = count < items.length
  const remaining = Math.max(0, items.length - count)
  const showMore = () => setCount((c) => Math.min(c + step, items.length))

  return { visible, hasMore, remaining, showMore }
}
