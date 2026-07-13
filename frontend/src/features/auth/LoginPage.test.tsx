import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { http, HttpResponse } from 'msw'
import { webcrypto } from 'node:crypto'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import LoginPage from '@/features/auth/LoginPage'

/**
 * LoginPage ↔ authApi ↔ authSlice entegrasyonu (MSW üzerinden):
 * başarılı giriş { user, token } döner, Redux'a yazılır ve /chat'e yönlenir;
 * backend hatası formda role="alert" ile gösterilir; misafir jetonsuz girer.
 */
beforeAll(() => {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
  }
  server.listen({ onUnhandledRequest: 'error' })
})
beforeEach(() => {
  localStorage.clear()
})
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

type PreloadedAuth = {
  user: { id: string; email: string; name?: string; guest?: boolean } | null
  token: string | null
  refreshToken: string | null
  guestId: string | null
}

function renderLogin(preloadedAuth?: PreloadedAuth) {
  const store = configureStore({
    reducer: { auth: authReducer },
    ...(preloadedAuth ? { preloadedState: { auth: preloadedAuth } } : {}),
  })
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/chat" element={<div>CHAT STUB</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  )
  return { store }
}

describe('LoginPage (authApi + MSW ile)', () => {
  it('başarılı girişte oturumu Redux’a yazar ve /chat’e yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderLogin()

    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Şifre'), 'gizli-sifre-123')
    await user.click(screen.getByRole('button', { name: 'Giriş Yap' }))

    await screen.findByText('CHAT STUB')
    const auth = store.getState().auth
    expect(auth.user?.email).toBe('zehra@example.com')
    expect(auth.token).toMatch(/^mock-token-/)
    // Kalıcılık: sayfa yenilemede oturum düşmesin diye localStorage'a yazıldı.
    expect(JSON.parse(localStorage.getItem('pax-auth')!).token).toBe(auth.token)
  })

  it('backend hatasını formda gösterir ve login’de kalır', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          { error: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
          { status: 401 },
        ),
      ),
    )
    const user = userEvent.setup()
    const { store } = renderLogin()

    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Şifre'), 'yanlis-sifre')
    await user.click(screen.getByRole('button', { name: 'Giriş Yap' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Invalid email or password')
    expect(screen.queryByText('CHAT STUB')).toBeNull()
    expect(store.getState().auth.user).toBeNull()
  })

  it('kayıtta kısa şifreyi backend’e gitmeden reddeder', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Kayıt ol' }))
    await user.type(screen.getByLabelText('Ad Soyad'), 'Zehra Bozkurt')
    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Şifre'), 'kisa')
    await user.type(screen.getByLabelText('Şifre (Tekrar)'), 'kisa')
    await user.click(screen.getByRole('button', { name: 'Kayıt Ol' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Şifre en az 8 karakter olmalıdır.')
  })

  it('başarılı kayıt oturum açar ve /chat’e yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderLogin()

    await user.click(screen.getByRole('button', { name: 'Kayıt ol' }))
    await user.type(screen.getByLabelText('Ad Soyad'), 'Zehra Bozkurt')
    await user.type(screen.getByLabelText('E-posta'), 'yeni@example.com')
    await user.type(screen.getByLabelText('Şifre'), 'gizli-sifre-123')
    await user.type(screen.getByLabelText('Şifre (Tekrar)'), 'gizli-sifre-123')
    await user.click(screen.getByRole('button', { name: 'Kayıt Ol' }))

    await screen.findByText('CHAT STUB')
    expect(store.getState().auth.user).toMatchObject({
      email: 'yeni@example.com',
      name: 'Zehra Bozkurt',
    })
    expect(store.getState().auth.token).toMatch(/^mock-token-/)
  })

  it('misafir girişi jetonsuz yerel oturum açar', async () => {
    const user = userEvent.setup()
    const { store } = renderLogin()

    await user.click(screen.getByRole('button', { name: 'Misafir olarak devam et' }))

    await screen.findByText('CHAT STUB')
    await waitFor(() => {
      expect(store.getState().auth.user).toMatchObject({ guest: true })
    })
    expect(store.getState().auth.token).toBeNull()
    // Misafir kimliği üretildi (X-Guest-Id için) ve kalıcılaştı.
    expect(store.getState().auth.guestId).toEqual(expect.any(String))
  })

  it('zaten misafir olan kullanıcı /login’de kalır (bounce edilmez), formu görür', () => {
    renderLogin({
      user: { id: 'guest', email: '', name: 'Misafir', guest: true },
      token: null,
      refreshToken: null,
      guestId: 'guest-xyz',
    })
    // Bounce olsaydı CHAT STUB görünürdü; misafir hesaba yükselmek için formda kalmalı.
    expect(screen.queryByRole('button', { name: 'Giriş Yap' })).not.toBeNull()
    expect(screen.queryByText('CHAT STUB')).toBeNull()
  })

  it('gerçek (misafir olmayan) oturum açıkken /login’den /chat’e yönlendirir', async () => {
    renderLogin({
      user: { id: '1', email: 'a@b.c', name: 'A' },
      token: 'jwt-1',
      refreshToken: 'refresh-1',
      guestId: null,
    })
    await screen.findByText('CHAT STUB')
  })
})
