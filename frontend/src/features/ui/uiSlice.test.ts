import { describe, expect, it } from 'vitest'
import reducer, {
  flightFiltersChanged,
  flightFiltersReset,
  hotelFiltersChanged,
  hotelFiltersReset,
  modalClosed,
  modalOpened,
  toastCleared,
  toastShown,
} from './uiSlice'

describe('uiSlice', () => {
  it('hotelFiltersChanged kısmi güncelleme yapar, diğer alanları korur', () => {
    let state = reducer(undefined, hotelFiltersChanged({ minStars: 4 }))
    state = reducer(state, hotelFiltersChanged({ sort: 'price-asc' }))
    expect(state.hotelFilters).toMatchObject({ minStars: 4, sort: 'price-asc', boardType: null })
  })

  it('hotelFiltersReset filtreleri sıfırlar', () => {
    let state = reducer(undefined, hotelFiltersChanged({ minStars: 5, maxPrice: 900 }))
    state = reducer(state, hotelFiltersReset())
    expect(state.hotelFilters).toEqual({
      minStars: null,
      boardType: null,
      maxPrice: null,
      sort: null,
    })
  })

  it('flightFiltersChanged/Reset çalışır', () => {
    let state = reducer(undefined, flightFiltersChanged({ nonstopOnly: true, airline: 'MockAir' }))
    expect(state.flightFilters).toMatchObject({ nonstopOnly: true, airline: 'MockAir' })
    state = reducer(state, flightFiltersReset())
    expect(state.flightFilters.nonstopOnly).toBe(false)
    expect(state.flightFilters.airline).toBeNull()
  })

  it('modal ve toast aç/kapa', () => {
    let state = reducer(undefined, modalOpened('reservation-cancel'))
    expect(state.activeModal).toBe('reservation-cancel')
    state = reducer(state, modalClosed())
    expect(state.activeModal).toBeNull()

    state = reducer(state, toastShown('Rezervasyon oluşturuldu'))
    expect(state.toast).toBe('Rezervasyon oluşturuldu')
    state = reducer(state, toastCleared())
    expect(state.toast).toBeNull()
  })
})
