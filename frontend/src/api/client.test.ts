import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { authApi, reservationApi } from '@/api'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
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
})
