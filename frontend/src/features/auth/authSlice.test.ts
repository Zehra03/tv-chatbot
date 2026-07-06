import { beforeEach, describe, expect, it, vi } from 'vitest'
import reducer, {
  guestSessionStarted,
  logout,
  sessionStarted,
  userRefreshed,
} from './authSlice'

/**
 * authSlice sözleşmesi: oturum { user, token } olarak tutulur, localStorage'a
 * ('pax-auth') aynalanır ki sayfa yenilemede düşmesin; logout ikisini de siler.
 */
const user = { id: '42', email: 'zehra@example.com', name: 'Zehra' }

beforeEach(() => {
  localStorage.clear()
})

describe('authSlice', () => {
  it('sessionStarted kullanıcı + jetonu yazar ve localStorage’a kalıcılaştırır', () => {
    const state = reducer(undefined, sessionStarted({ user, token: 'jwt-1' }))

    expect(state.user).toEqual(user)
    expect(state.token).toBe('jwt-1')
    expect(JSON.parse(localStorage.getItem('pax-auth')!)).toEqual({ user, token: 'jwt-1' })
  })

  it('logout state’i ve localStorage’ı temizler', () => {
    const loggedIn = reducer(undefined, sessionStarted({ user, token: 'jwt-1' }))
    const state = reducer(loggedIn, logout())

    expect(state).toEqual({ user: null, token: null })
    expect(localStorage.getItem('pax-auth')).toBeNull()
  })

  it('guestSessionStarted jetonsuz misafir oturumu açar', () => {
    const state = reducer(undefined, guestSessionStarted())

    expect(state.user).toMatchObject({ guest: true, name: 'Misafir' })
    expect(state.token).toBeNull()
  })

  it('userRefreshed açık oturumun kullanıcısını tazeler, kapalı oturuma dokunmaz', () => {
    const loggedIn = reducer(undefined, sessionStarted({ user, token: 'jwt-1' }))
    const refreshed = reducer(loggedIn, userRefreshed({ ...user, name: 'Zehra B.' }))
    expect(refreshed.user?.name).toBe('Zehra B.')
    expect(refreshed.token).toBe('jwt-1')

    const loggedOut = reducer(undefined, userRefreshed(user))
    expect(loggedOut.user).toBeNull()
  })

  it('modül yüklenirken localStorage’daki oturumu geri yükler', async () => {
    localStorage.setItem('pax-auth', JSON.stringify({ user, token: 'jwt-stored' }))
    vi.resetModules()

    const { default: freshReducer } = await import('./authSlice')
    const state = freshReducer(undefined, { type: '@@INIT' })

    expect(state).toEqual({ user, token: 'jwt-stored' })
  })

  it('bozuk localStorage kaydında boş oturumla başlar', async () => {
    localStorage.setItem('pax-auth', 'bozuk-json{{')
    vi.resetModules()

    const { default: freshReducer } = await import('./authSlice')
    const state = freshReducer(undefined, { type: '@@INIT' })

    expect(state).toEqual({ user: null, token: null })
  })
})
