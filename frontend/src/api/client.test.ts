import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import {
  authApi,
  reservationApi,
  setAuthToken,
  setGuestId,
  setRefreshToken,
  TOKENS_REFRESHED_EVENT,
  UNAUTHORIZED_EVENT,
} from '@/api'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  setAuthToken(null)
  setRefreshToken(null)
  setGuestId(null)
})
afterAll(() => server.close())

/**
 * apiClient response interceptor sözleşmesi:
 * - JSON beklenirken HTML/ham string dönerse (ör. MSW baypas + Vite SPA
 *   fallback'i) normalize ApiError ile REDDEDER — sayfalar çökmek yerine
 *   isError akışına düşer.
 * - 204/boş gövde ve gerçek JSON yanıtlar aynen geçer; hata normalizasyonu
 *   (status + message) değişmez.
 */
describe('apiClient interceptor (MSW ile)', () => {
  it('200 + text/html (SPA fallback) yanıtını ApiError olarak reddeder', async () => {
    server.use(
      http.get('/api/v1/reservations', () =>
        HttpResponse.html('<!doctype html><html><body><div id="root"></div></body></html>'),
      ),
    )

    await expect(reservationApi.list()).rejects.toMatchObject({
      status: 200,
      message: expect.stringContaining('MSW'),
    })
  })

  it('204 boş gövdeli yanıt (logout) sorunsuz geçer', async () => {
    await expect(authApi.logout()).resolves.toBeUndefined()
  })

  it('normal JSON yanıtı aynen döner', async () => {
    const list = await reservationApi.list()
    expect(Array.isArray(list)).toBe(true)
    expect(list.length).toBeGreaterThan(0)
  })

  it('backend hatasını {status, message} olarak normalize etmeye devam eder', async () => {
    server.use(
      http.get('/api/v1/reservations', () =>
        HttpResponse.json({ message: 'Sunucu hatası.' }, { status: 500 }),
      ),
    )

    await expect(reservationApi.list()).rejects.toMatchObject({
      status: 500,
      message: 'Sunucu hatası.',
    })
  })

  it('setAuthToken sonrası istekler Authorization: Bearer başlığı taşır', async () => {
    let seenHeader: string | null = null
    server.use(
      http.get('/api/v1/auth/me', ({ request }) => {
        seenHeader = request.headers.get('Authorization')
        return HttpResponse.json({ id: '1', email: 'a@b.c' })
      }),
    )

    setAuthToken('jwt-123')
    await authApi.me()

    expect(seenHeader).toBe('Bearer jwt-123')
  })

  it('jetonsuz isteklerde Authorization başlığı gönderilmez', async () => {
    let seenHeader: string | null = 'sentinel'
    server.use(
      http.get('/api/v1/auth/me', ({ request }) => {
        seenHeader = request.headers.get('Authorization')
        return HttpResponse.json({ id: '1', email: 'a@b.c' })
      }),
    )

    await authApi.me()

    expect(seenHeader).toBeNull()
  })

  it('misafirde (jeton yok, guestId var) X-Guest-Id başlığı gönderir, Authorization göndermez', async () => {
    let seenGuest: string | null = null
    let seenAuth: string | null = 'sentinel'
    server.use(
      http.get('/api/v1/auth/me', ({ request }) => {
        seenGuest = request.headers.get('X-Guest-Id')
        seenAuth = request.headers.get('Authorization')
        return HttpResponse.json({ id: '1', email: 'a@b.c' })
      }),
    )

    setGuestId('guest-abc')
    await authApi.me()

    expect(seenGuest).toBe('guest-abc')
    expect(seenAuth).toBeNull()
  })

  it('jeton varken Authorization öncelikli olur, X-Guest-Id gönderilmez', async () => {
    let seenGuest: string | null = 'sentinel'
    let seenAuth: string | null = null
    server.use(
      http.get('/api/v1/auth/me', ({ request }) => {
        seenGuest = request.headers.get('X-Guest-Id')
        seenAuth = request.headers.get('Authorization')
        return HttpResponse.json({ id: '1', email: 'a@b.c' })
      }),
    )

    setAuthToken('jwt-123')
    setGuestId('guest-abc')
    await authApi.me()

    expect(seenAuth).toBe('Bearer jwt-123')
    expect(seenGuest).toBeNull()
  })

  it('refresh jetonu yokken 401 doğrudan UNAUTHORIZED_EVENT yayınlar', async () => {
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json({ message: 'Jeton süresi doldu.' }, { status: 401 }),
      ),
    )
    const listener = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, listener)

    setAuthToken('jwt-eski')
    await expect(authApi.me()).rejects.toMatchObject({ status: 401 })
    window.removeEventListener(UNAUTHORIZED_EVENT, listener)

    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('jetonlu istek 401 dönünce refresh jetonuyla sessizce yeniler ve isteği tekrarlar', async () => {
    let meCalls = 0
    server.use(
      http.get('/api/v1/auth/me', ({ request }) => {
        meCalls += 1
        // İlk çağrı eski jetonla → 401; refresh sonrası retry yeni jetonla → 200.
        if (request.headers.get('Authorization') === 'Bearer jwt-yeni') {
          return HttpResponse.json({ id: '1', email: 'a@b.c' })
        }
        return HttpResponse.json({ message: 'Jeton süresi doldu.' }, { status: 401 })
      }),
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({
          user: { id: '1', email: 'a@b.c' },
          token: 'jwt-yeni',
          refreshToken: 'refresh-yeni',
        }),
      ),
    )
    const unauthorized = vi.fn()
    const refreshed = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, unauthorized)
    window.addEventListener(TOKENS_REFRESHED_EVENT, refreshed)

    setAuthToken('jwt-eski')
    setRefreshToken('refresh-eski')
    const me = await authApi.me()

    window.removeEventListener(UNAUTHORIZED_EVENT, unauthorized)
    window.removeEventListener(TOKENS_REFRESHED_EVENT, refreshed)

    expect(me).toMatchObject({ id: '1' })
    expect(meCalls).toBe(2) // 401, ardından retry 200
    expect(refreshed).toHaveBeenCalledTimes(1)
    expect(unauthorized).not.toHaveBeenCalled()
  })

  it('refresh de 401 dönerse oturumu düşürür (UNAUTHORIZED_EVENT)', async () => {
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json({ message: 'Jeton süresi doldu.' }, { status: 401 }),
      ),
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({ message: 'Refresh geçersiz.' }, { status: 401 }),
      ),
    )
    const unauthorized = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, unauthorized)

    setAuthToken('jwt-eski')
    setRefreshToken('refresh-eski')
    await expect(authApi.me()).rejects.toMatchObject({ status: 401 })

    window.removeEventListener(UNAUTHORIZED_EVENT, unauthorized)
    expect(unauthorized).toHaveBeenCalledTimes(1)
  })

  it('login’in kendi 401’i (hatalı şifre) oturum-düşmesi olayı YAYINLAMAZ', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json({ message: 'Invalid email or password' }, { status: 401 }),
      ),
    )
    const listener = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, listener)

    // Süresi dolmuş bir jetonla yeniden giriş denemesi senaryosu.
    setAuthToken('jwt-eski')
    await expect(authApi.login({ email: 'a@b.c', password: 'yanlis' })).rejects.toMatchObject({
      status: 401,
    })
    window.removeEventListener(UNAUTHORIZED_EVENT, listener)

    expect(listener).not.toHaveBeenCalled()
  })
})
