import { beforeEach, describe, expect, it, vi } from 'vitest'
import reducer, {
  guestSessionStarted,
  logout,
  sessionStarted,
  tokensRefreshed,
  userRefreshed,
} from './authSlice'

/**
 * authSlice sözleşmesi: oturum { user, token, refreshToken } olarak tutulur,
 * localStorage'a ('pax-auth') aynalanır ki sayfa yenilemede düşmesin; logout
 * hepsini siler. tokensRefreshed sessiz refresh sonrası jeton çiftini tazeler.
 */
const user = { id: '42', email: 'zehra@example.com', name: 'Zehra' }

beforeEach(() => {
  localStorage.clear()
})

describe('authSlice', () => {
  it('sessionStarted kullanıcı + jeton çiftini yazar ve localStorage’a kalıcılaştırır', () => {
    const state = reducer(undefined, sessionStarted({ user, token: 'jwt-1', refreshToken: 'refresh-1' }))

    expect(state.user).toEqual(user)
    expect(state.token).toBe('jwt-1')
    expect(state.refreshToken).toBe('refresh-1')
    expect(JSON.parse(localStorage.getItem('pax-auth')!)).toEqual({
      user,
      token: 'jwt-1',
      refreshToken: 'refresh-1',
    })
  })

  it('tokensRefreshed açık oturumun jeton çiftini tazeler, kapalı oturuma dokunmaz', () => {
    const loggedIn = reducer(undefined, sessionStarted({ user, token: 'jwt-1', refreshToken: 'refresh-1' }))
    const rotated = reducer(loggedIn, tokensRefreshed({ token: 'jwt-2', refreshToken: 'refresh-2' }))
    expect(rotated.token).toBe('jwt-2')
    expect(rotated.refreshToken).toBe('refresh-2')
    expect(rotated.user).toEqual(user)
    expect(JSON.parse(localStorage.getItem('pax-auth')!)).toMatchObject({
      token: 'jwt-2',
      refreshToken: 'refresh-2',
    })

    const loggedOut = reducer(undefined, tokensRefreshed({ token: 'x', refreshToken: 'y' }))
    expect(loggedOut.user).toBeNull()
    expect(loggedOut.token).toBeNull()
  })

  it('logout state’i ve localStorage’ı temizler', () => {
    const loggedIn = reducer(undefined, sessionStarted({ user, token: 'jwt-1', refreshToken: 'refresh-1' }))
    const state = reducer(loggedIn, logout())

    expect(state).toEqual({ user: null, token: null, refreshToken: null })
    expect(localStorage.getItem('pax-auth')).toBeNull()
  })

  it('guestSessionStarted jetonsuz misafir oturumu açar', () => {
    const state = reducer(undefined, guestSessionStarted())

    expect(state.user).toMatchObject({ guest: true, name: 'Misafir' })
    expect(state.token).toBeNull()
    expect(state.refreshToken).toBeNull()
  })

  it('userRefreshed açık oturumun kullanıcısını tazeler, kapalı oturuma dokunmaz', () => {
    const loggedIn = reducer(undefined, sessionStarted({ user, token: 'jwt-1', refreshToken: 'refresh-1' }))
    const refreshed = reducer(loggedIn, userRefreshed({ ...user, name: 'Zehra B.' }))
    expect(refreshed.user?.name).toBe('Zehra B.')
    expect(refreshed.token).toBe('jwt-1')

    const loggedOut = reducer(undefined, userRefreshed(user))
    expect(loggedOut.user).toBeNull()
  })

  it('modül yüklenirken localStorage’daki oturumu geri yükler', async () => {
    localStorage.setItem(
      'pax-auth',
      JSON.stringify({ user, token: 'jwt-stored', refreshToken: 'refresh-stored' }),
    )
    vi.resetModules()

    const { default: freshReducer } = await import('./authSlice')
    const state = freshReducer(undefined, { type: '@@INIT' })

    expect(state).toEqual({ user, token: 'jwt-stored', refreshToken: 'refresh-stored' })
  })

  it('bozuk localStorage kaydında boş oturumla başlar', async () => {
    localStorage.setItem('pax-auth', 'bozuk-json{{')
    vi.resetModules()

    const { default: freshReducer } = await import('./authSlice')
    const state = freshReducer(undefined, { type: '@@INIT' })

    expect(state).toEqual({ user: null, token: null, refreshToken: null })
  })
})
