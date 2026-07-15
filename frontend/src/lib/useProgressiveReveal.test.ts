import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useProgressiveReveal } from '@/lib/useProgressiveReveal'

const items = Array.from({ length: 12 }, (_, i) => i)

describe('useProgressiveReveal (madde 9 — Daha fazla göster)', () => {
  it('ilk `step` öğeyi gösterir, kalanı sayar', () => {
    const { result } = renderHook(() => useProgressiveReveal(items, 5))
    expect(result.current.visible).toHaveLength(5)
    expect(result.current.hasMore).toBe(true)
    expect(result.current.remaining).toBe(7)
  })

  it('showMore her çağrıda `step` kadar açar ve sonda durur', () => {
    const { result } = renderHook(() => useProgressiveReveal(items, 5))
    act(() => result.current.showMore())
    expect(result.current.visible).toHaveLength(10)
    expect(result.current.remaining).toBe(2)
    act(() => result.current.showMore())
    expect(result.current.visible).toHaveLength(12)
    expect(result.current.hasMore).toBe(false)
    expect(result.current.remaining).toBe(0)
  })

  it('öğe sayısı step altına inince hasMore çıkmaz', () => {
    const { result } = renderHook(() => useProgressiveReveal([1, 2, 3], 5))
    expect(result.current.visible).toHaveLength(3)
    expect(result.current.hasMore).toBe(false)
    expect(result.current.remaining).toBe(0)
  })

  it('liste referansı değişince ilk sayfaya döner (yeni arama)', () => {
    const { result, rerender } = renderHook(({ list }) => useProgressiveReveal(list, 5), {
      initialProps: { list: items },
    })
    act(() => result.current.showMore())
    expect(result.current.visible).toHaveLength(10)

    const next = Array.from({ length: 8 }, (_, i) => i + 100)
    rerender({ list: next })
    expect(result.current.visible).toHaveLength(5)
  })
})
