import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { renderHook, act, cleanup } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import type { ReactNode } from 'react'
import { server } from '@/mocks/server'
import authReducer, { sessionStarted } from '@/features/auth/authSlice'
import { useLogout } from '@/features/auth/useLogout'

/**
 * Çıkışın sunucu tarafını da kapattığının kanıtı. Regresyonda `authApi.logout()` hiç
 * çağrılmıyordu: refresh jetonu sunucuda geçerli kalıyor, "Çıkış" yalnız tarayıcının
 * kopyasını siliyordu.
 */

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const USER = { id: 'u-1', email: 'a@b.co', name: 'Ada' }

function setup() {
  const store = configureStore({ reducer: { auth: authReducer } })
  act(() => {
    store.dispatch(sessionStarted({ user: USER, token: 'token-1', refreshToken: 'refresh-1' }))
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <Provider store={store}>
      <MemoryRouter>{children}</MemoryRouter>
    </Provider>
  )
  const { result } = renderHook(() => useLogout(), { wrapper })
  return { store, logout: result.current }
}

describe('useLogout', () => {
  it('sunucuya JETONLA çıkış isteği atar — oturum sunucuda da kapanır', async () => {
    const seen: (string | null)[] = []
    server.use(
      http.post('*/api/v1/auth/logout', ({ request }) => {
        seen.push(request.headers.get('Authorization'))
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { store, logout } = setup()

    await act(async () => {
      await logout()
    })

    // İstek atıldı VE jeton hâlâ takılıyken atıldı: dispatch(logout()) önce gelseydi
    // setAuthToken(null) çalışır, başlık düşerdi ve sunucu oturumu tanıyamazdı.
    expect(seen).toEqual(['Bearer token-1'])
    expect(store.getState().auth.user).toBeNull()
    expect(store.getState().auth.refreshToken).toBeNull()
  })

  it('sunucuya ulaşılamasa da yerel oturumu kapatır', async () => {
    server.use(http.post('*/api/v1/auth/logout', () => HttpResponse.error()))
    const { store, logout } = setup()

    await act(async () => {
      await logout()
    })

    // Kullanıcı "çıktım" dediyse çıkmıştır — ağ hatası onu oturumda tutmamalı.
    expect(store.getState().auth.user).toBeNull()
    expect(store.getState().auth.token).toBeNull()
  })
})
