/**
 * Gerçek backend'e karşı HTTP-seviyesi uçtan uca auth testi.
 *
 * MSW YOK — üretim kodundaki gerçek zincir kullanılır: authApi → apiClient
 * (Authorization interceptor'ı + ApiError normalizasyonu) → canlı backend.
 * Node ortamında koşar (CORS/jsdom kısıtı yok); backend'in CORS'u tarayıcı
 * içi akışta ayrıca devrededir.
 *
 * Çalıştırma (backend + Postgres ayakta olmalı) — iki yol:
 *   1) BACKEND_E2E_URL=http://localhost:8080 npm run test -- realAuth
 *   2) src/test/e2e.local.json dosyasına { "backendUrl": "http://localhost:8080" }
 *      yazıp: npm run test -- realAuth   (*.local git'e girmez)
 *
 * İkisi de yoksa dosya atlanır — normal `npm run test` ve CI etkilenmez.
 * Testler kalıcı iz bırakır: her koşuda benzersiz e-postayla yeni kullanıcı
 * yaratılır (users tablosunda e2e.* kayıtları birikir; dev DB'de kabul
 * edilebilir, paylaşılan ortamda çalıştırmayın).
 */
// @vitest-environment node
import { readFileSync } from 'node:fs'
import { beforeAll, describe, expect, it } from 'vitest'
import { apiClient, authApi, setAuthToken, type AuthResponse } from '@/api'

function readLocalConfig(): string {
  try {
    const raw = readFileSync(new URL('./e2e.local.json', import.meta.url), 'utf8')
    return (JSON.parse(raw) as { backendUrl?: string }).backendUrl ?? ''
  } catch {
    return ''
  }
}

const baseUrl = process.env.BACKEND_E2E_URL || readLocalConfig()

describe.skipIf(!baseUrl)('Gerçek backend auth E2E', () => {
  const email = `e2e.${Date.now()}@paxassist.test`
  const password = 'S3cure-e2e-parola!'
  const name = 'E2E Testçisi'
  let session: AuthResponse

  beforeAll(async () => {
    apiClient.defaults.baseURL = baseUrl
    setAuthToken(null)

    // Kimlik probu: PaxAssist backend'i yanlış kimlikte 400/401 + { error, message }
    // JSON'u döner. Başka bir sunucu (nginx 404, Vite SPA fallback...) dönmez —
    // erken ve anlaşılır biçimde patlayalım.
    const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'probe@paxassist.test', password: 'yanlis-parola' }),
    }).catch((cause) => {
      throw new Error(`${baseUrl} adresine ulaşılamadı — backend ayakta mı? (${String(cause)})`)
    })
    const body = await res.text()
    const looksLikeOurBackend =
      (res.status === 400 || res.status === 401) && body.includes('"error"')
    if (!looksLikeOurBackend) {
      throw new Error(
        `${baseUrl} PaxAssist backend'i gibi görünmüyor: ` +
          `HTTP ${res.status}, gövde: ${body.slice(0, 200)}`,
      )
    }
  })

  it('kayıt: { user, token, refreshToken } döner, kullanıcı bilgisi eşleşir', async () => {
    session = await authApi.register({ email, password, name })
    expect(session.user.email.toLowerCase()).toBe(email.toLowerCase())
    expect(session.user.name).toBe(name)
    expect(session.user.id).toEqual(expect.any(String))
    expect(session.token).toEqual(expect.any(String))
    expect(session.token.length).toBeGreaterThan(0)
    expect(session.refreshToken).toEqual(expect.any(String))
    expect(session.refreshToken.length).toBeGreaterThan(0)
  })

  it('aynı e-postayla ikinci kayıt: 409 + mesaj', async () => {
    await expect(authApi.register({ email, password, name })).rejects.toMatchObject({
      status: 409,
      message: expect.stringContaining('already exists'),
    })
  })

  it('yanlış şifreyle giriş: 401 + kullanıcıya gösterilebilir mesaj', async () => {
    await expect(authApi.login({ email, password: 'tamamen-yanlis-1' })).rejects.toMatchObject({
      status: 401,
      message: 'Invalid email or password',
    })
  })

  it('doğru şifreyle giriş: jeton + aynı kullanıcı', async () => {
    const login = await authApi.login({ email, password })
    expect(login.user.id).toBe(session.user.id)
    expect(login.token.length).toBeGreaterThan(0)
    expect(login.refreshToken.length).toBeGreaterThan(0)
    session = login
  })

  it('refresh: yeni access + refresh çifti döner; eski refresh jetonu rotation ile geçersizleşir', async () => {
    const before = session
    const refreshed = await authApi.refresh(before.refreshToken)
    expect(refreshed.user.id).toBe(before.user.id)
    expect(refreshed.token.length).toBeGreaterThan(0)
    expect(refreshed.refreshToken.length).toBeGreaterThan(0)
    expect(refreshed.refreshToken).not.toBe(before.refreshToken)

    // Rotation: rotate edilmiş eski refresh jetonu artık kabul edilmez (401).
    await expect(authApi.refresh(before.refreshToken)).rejects.toMatchObject({ status: 401 })
    session = refreshed
  })

  it('jetonlu GET /auth/me: interceptor Authorization ekler, kullanıcı döner', async () => {
    setAuthToken(session.token)
    const me = await authApi.me()
    expect(me).toMatchObject({ id: session.user.id, name })
    expect(me.email.toLowerCase()).toBe(email.toLowerCase())
  })

  it('bozuk jetonla GET /auth/me: 401', async () => {
    setAuthToken('bozuk-jeton')
    await expect(authApi.me()).rejects.toMatchObject({ status: 401 })
    setAuthToken(session.token)
  })

  it('jetonsuz GET /auth/me: 401', async () => {
    setAuthToken(null)
    await expect(authApi.me()).rejects.toMatchObject({ status: 401 })
    setAuthToken(session.token)
  })

  it('kısa şifreyle kayıt: 400 VALIDATION_ERROR + alan mesajı', async () => {
    await expect(
      authApi.register({ email: `kisa.${Date.now()}@paxassist.test`, password: 'kisa' }),
    ).rejects.toMatchObject({
      status: 400,
      message: expect.stringContaining('password'),
    })
  })

  it('logout: 204 döner (jeton istemcide atılır)', async () => {
    await expect(authApi.logout()).resolves.toBeUndefined()
  })
})
