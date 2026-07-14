import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { webcrypto } from 'node:crypto'
import { server } from '@/mocks/server'
import authReducer, { type SessionUser } from '@/features/auth/authSlice'
import { ProfilePage } from '@/features/profile/ProfilePage'

/**
 * ProfilePage e-posta satır-içi düzenleme (authApi + MSW ile): "Düzenle" formu
 * açar, geçerli e-posta PATCH /auth/me'ye gider ve dönen kullanıcı Redux'a yazılır;
 * geçersiz biçim backend'e gitmeden reddedilir; misafirde düzenleme görünmez.
 * PATCH handler'ı mock-oturum defterine bağlı olmasın diye testte override edilir.
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

const member: SessionUser = { id: '1', email: 'eski@example.com', name: 'Zehra Bozkurt' }
const guest: SessionUser = { id: 'guest', email: '', name: 'Misafir', guest: true }

function renderProfile(user: SessionUser) {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: { user, token: user.guest ? null : 'jwt-1', refreshToken: null, guestId: null },
    },
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/profile']}>
          <Routes>
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/login" element={<div>LOGIN STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return { store }
}

describe('ProfilePage e-posta düzenleme', () => {
  it('geçerli yeni e-postayı kaydeder ve Redux’ı günceller', async () => {
    let received = ''
    server.use(
      http.patch('/api/v1/auth/me', async ({ request }) => {
        const body = (await request.json()) as { email: string }
        received = body.email
        return HttpResponse.json({ id: '1', email: body.email, name: 'Zehra Bozkurt' })
      }),
    )
    const user = userEvent.setup()
    const { store } = renderProfile(member)

    await user.click(screen.getByRole('button', { name: 'E-postayı düzenle' }))
    const input = screen.getByLabelText('E-posta')
    await user.clear(input)
    await user.type(input, 'yeni@example.com')
    await user.click(screen.getByRole('button', { name: 'Kaydet' }))

    await waitFor(() => {
      expect(store.getState().auth.user?.email).toBe('yeni@example.com')
    })
    expect(received).toBe('yeni@example.com')
    // Form kapandı, güncel e-posta görünüyor.
    expect(screen.queryByRole('button', { name: 'Kaydet' })).toBeNull()
    expect(screen.getAllByText('yeni@example.com').length).toBeGreaterThan(0)
  })

  it('geçersiz biçimli e-postayı backend’e gitmeden reddeder', async () => {
    const user = userEvent.setup()
    const { store } = renderProfile(member)

    await user.click(screen.getByRole('button', { name: 'E-postayı düzenle' }))
    const input = screen.getByLabelText('E-posta')
    await user.clear(input)
    await user.type(input, 'gecersiz')
    await user.click(screen.getByRole('button', { name: 'Kaydet' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Geçerli bir e-posta girin.')
    expect(store.getState().auth.user?.email).toBe('eski@example.com')
  })

  it('backend hatasında e-postayı değiştirmez ve hatayı gösterir', async () => {
    server.use(
      http.patch('/api/v1/auth/me', () =>
        HttpResponse.json(
          { error: 'EMAIL_ALREADY_EXISTS', message: 'Bu e-posta zaten kayıtlı: dolu@example.com' },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    const { store } = renderProfile(member)

    await user.click(screen.getByRole('button', { name: 'E-postayı düzenle' }))
    const input = screen.getByLabelText('E-posta')
    await user.clear(input)
    await user.type(input, 'dolu@example.com')
    await user.click(screen.getByRole('button', { name: 'Kaydet' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('zaten kayıtlı')
    expect(store.getState().auth.user?.email).toBe('eski@example.com')
  })

  it('İptal düzenlemeyi kapatır ve e-postayı değiştirmez', async () => {
    const user = userEvent.setup()
    const { store } = renderProfile(member)

    await user.click(screen.getByRole('button', { name: 'E-postayı düzenle' }))
    await user.clear(screen.getByLabelText('E-posta'))
    await user.type(screen.getByLabelText('E-posta'), 'vazgectim@example.com')
    await user.click(screen.getByRole('button', { name: 'İptal' }))

    expect(screen.queryByRole('button', { name: 'Kaydet' })).toBeNull()
    expect(store.getState().auth.user?.email).toBe('eski@example.com')
  })

  it('misafir oturumunda e-posta düzenleme görünmez', () => {
    renderProfile(guest)
    expect(screen.queryByRole('button', { name: 'E-postayı düzenle' })).toBeNull()
  })
})
