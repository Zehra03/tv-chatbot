import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { ResultsPanel } from '@/features/chat/ResultsPanel'
import { hotelFixtures } from '@/mocks/fixtures'
import type { ResultCard } from '@/types'

afterEach(cleanup)

const cards: ResultCard[] = hotelFixtures
  .slice(0, 2)
  .map((product) => ({ productType: 'hotel', product }))

function renderPanel(loading: boolean) {
  const store = configureStore({ reducer: { reservationDraft: reservationDraftReducer } })
  render(
    <Provider store={store}>
      <MemoryRouter>
        <ResultsPanel cards={cards} loading={loading} />
      </MemoryRouter>
    </Provider>,
  )
}

describe('ResultsPanel — arama geri bildirimi (PPMO K24)', () => {
  it('yeni arama sürerken "Aranıyor…" spinner\'ı gösterir', () => {
    renderPanel(true)
    expect(screen.getByText('Aranıyor…')).toBeTruthy()
    // Kartlar kaybolmaz — tazelendiklerini belli edecek şekilde görünür kalır.
    expect(screen.getByText(hotelFixtures[0].hotelName)).toBeTruthy()
  })

  it('arama yokken spinner göstermez', () => {
    renderPanel(false)
    expect(screen.queryByText('Aranıyor…')).toBeNull()
    expect(screen.getByText(hotelFixtures[0].hotelName)).toBeTruthy()
  })
})
