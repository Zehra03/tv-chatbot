import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import chatReducer, { type ChatState } from '@/features/chat/chatSlice'
import { CriteriaChips } from '@/features/chat/CriteriaChips'

afterEach(cleanup)

function renderChips(chat: Partial<ChatState>) {
  const store = configureStore({
    reducer: { chat: chatReducer },
    preloadedState: {
      chat: { sessionId: null, messages: [], epoch: 0, ...chat } as ChatState,
    },
  })
  return render(
    <Provider store={store}>
      <CriteriaChips />
    </Provider>,
  )
}

describe('CriteriaChips', () => {
  it('otel niyetinde destination konaklama şehri olarak etiketlenir', () => {
    renderChips({
      accumulatedCriteria: { intent: 'hotel', criteria: { destination: 'Antalya', adults: 2 } },
    })

    expect(screen.getByText('Otel araması')).toBeTruthy()
    expect(screen.getByText('Nerede / Şehir: Antalya')).toBeTruthy()
    expect(screen.getByText('Yetişkin: 2')).toBeTruthy()
    // Uçuşun varış etiketi otelde sızmamalı.
    expect(screen.queryByText('Nereye: Antalya')).toBeNull()
  })

  it('uçuş niyetinde destination varış noktası olarak etiketlenir', () => {
    renderChips({
      accumulatedCriteria: {
        intent: 'flight',
        criteria: { origin: 'İstanbul', destination: 'Antalya', tripType: 'round_trip' },
      },
    })

    expect(screen.getByText('Uçuş araması')).toBeTruthy()
    expect(screen.getByText('Nereden: İstanbul')).toBeTruthy()
    expect(screen.getByText('Nereye: Antalya')).toBeTruthy()
    expect(screen.getByText('Yön: Gidiş-dönüş')).toBeTruthy()
    expect(screen.queryByText('Nerede / Şehir: Antalya')).toBeNull()
  })

  it('kriter yokken hiç render edilmez', () => {
    const { container } = renderChips({})
    expect(container.firstChild).toBeNull()
  })

  it('niyet beklenmedik gelse bile ham anahtar göstermez', () => {
    // Savunma: intent "flight" ama otel alanları dolu (backend yanlış etiketlerse).
    // adults/checkIn uçuş tablosunda yok → yine de okunur etikete düşmeli.
    renderChips({
      accumulatedCriteria: {
        intent: 'flight',
        criteria: { adults: 2, checkIn: '2026-08-01' },
      } as never,
    })

    expect(screen.getByText('Yetişkin: 2')).toBeTruthy()
    expect(screen.getByText('Giriş: 2026-08-01')).toBeTruthy()
    expect(screen.queryByText(/^adults:/)).toBeNull()
  })
})
