import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useDelayedFlag } from '@/lib/useDelayedFlag'

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

describe('useDelayedFlag', () => {
  it('active gecikme boyunca sürerse true olur', () => {
    const { result } = renderHook(() => useDelayedFlag(true, 1800))
    expect(result.current).toBe(false)
    act(() => vi.advanceTimersByTime(1800))
    expect(result.current).toBe(true)
  })

  it('gecikme dolmadan active kapanırsa hiç açılmaz', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 1800), {
      initialProps: { active: true },
    })
    act(() => vi.advanceTimersByTime(1000))
    rerender({ active: false })
    act(() => vi.advanceTimersByTime(2000))
    expect(result.current).toBe(false)
  })

  it('active kapanınca anında sıfırlanır', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 1800), {
      initialProps: { active: true },
    })
    act(() => vi.advanceTimersByTime(1800))
    expect(result.current).toBe(true)
    rerender({ active: false })
    expect(result.current).toBe(false)
  })
})
