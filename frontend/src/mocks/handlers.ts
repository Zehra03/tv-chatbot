import { http, HttpResponse, type RequestHandler } from 'msw'
import type { FlightSearchCriteria, HotelSearchCriteria, Reservation } from '@/types'
import type {
  AuthUser,
  CreateReservationRequest,
  LoginRequest,
  RegisterRequest,
  ReservationPreview,
  SendMessageRequest,
} from '@/api'
import { flightFixtures, hotelFixtures, reservationFixtures } from './fixtures'
import {
  deleteSessionState,
  getSessionState,
  listSessionStates,
  norm,
  processMessage,
} from './chatEngine'

/**
 * MSW istek yakalayıcıları — backend sözleşmesini (`/api/v1/*`) birebir taklit eder
 * (docs/frontend-architecture.md §6). Yalnızca development'ta çalışır (main.tsx guard'ı).
 * Gerçek backend'e geçiş: worker'ı kapat + VITE_API_BASE_URL'i ayarla — bileşen/hook
 * değişmez. Fixture verileri ⚠️ MOCK'tur; fiyat/uygunluk uydurulmaz (bkz. fixtures).
 */

/** Oluşturulan rezervasyonlar için tohumlanmış, çağrılar arası kalıcı bellek deposu. */
const reservations: Reservation[] = reservationFixtures.map((r) => ({
  ...r,
  passengers: r.passengers?.map((p) => ({ ...p })),
}))
let nextReservationId = 2000

/**
 * Mock oturum — gerçek backend'in kalıcı kullanıcı tablosunun karşılığı olarak
 * localStorage'a yazılır: sayfa yenilenince modül state'i sıfırlansa da
 * GET /auth/me saklı jetonu tanımaya devam eder (istemci oturumu kalıcıdır).
 * Storage yoksa (kısıtlı ortam) bellek kopyasıyla çalışır.
 */
interface MockAuthSession {
  user: AuthUser
  token: string
  refreshToken: string
}
const MOCK_AUTH_KEY = 'pax-msw-auth'
let mockAuthMemory: MockAuthSession | null = null
/** Aynı oturumda tekrar kayıt denemesi 409 döndürsün diye (backend paritesi). */
const registeredEmails = new Set<string>()

function readAuthSession(): MockAuthSession | null {
  try {
    const raw = localStorage.getItem(MOCK_AUTH_KEY)
    return raw ? (JSON.parse(raw) as MockAuthSession) : mockAuthMemory
  } catch {
    return mockAuthMemory
  }
}

function writeAuthSession(session: MockAuthSession | null) {
  mockAuthMemory = session
  try {
    if (session) localStorage.setItem(MOCK_AUTH_KEY, JSON.stringify(session))
    else localStorage.removeItem(MOCK_AUTH_KEY)
  } catch {
    /* bellek kopyası yeter */
  }
}

/** Backend'in çift-jeton yanıtı gibi yeni bir mock oturum (access + refresh) üretir. */
function issueSession(user: AuthUser): MockAuthSession {
  return {
    user,
    token: `mock-token-${crypto.randomUUID()}`,
    refreshToken: `mock-refresh-${crypto.randomUUID()}`,
  }
}

/** Backend'in ErrorResponse kaydıyla aynı gövde: { error, message, timestamp }. */
function errorBody(error: string, message: string) {
  return { error, message, timestamp: new Date().toISOString() }
}

/** Ürün-tipi bağımsız özet — preview/create için fixture'lardan ürün çözer. */
function findProduct(id: string) {
  const h = hotelFixtures.find((x) => x.id === id)
  if (h) {
    return {
      productType: 'hotel' as const,
      title: h.hotelName,
      summary: `${h.region} · ${h.stars}★ · ${h.boardType}`,
      price: h.price,
      currency: h.currency,
    }
  }
  const f = flightFixtures.find((x) => x.id === id)
  if (f) {
    return {
      productType: 'flight' as const,
      title: `${f.airline} ${f.origin} → ${f.destination}`,
      summary: `${f.departTime} · ${f.stops === 0 ? 'direkt' : `${f.stops} aktarma`} · ${f.baggage}`,
      price: f.price,
      currency: f.currency,
    }
  }
  return undefined
}

const notFound = (message: string) => HttpResponse.json({ message }, { status: 404 })

export const handlers: RequestHandler[] = [
  // ── Chat ────────────────────────────────────────────────────────────────
  http.post('/api/v1/chat', async ({ request }) => {
    const body = (await request.json()) as SendMessageRequest
    return HttpResponse.json(processMessage(body.sessionId, body.message ?? ''))
  }),

  // Statik path, ':sessionId' kalıbından ÖNCE kayıtlı olmalı (MSW sıra ile eşler).
  http.get('/api/v1/chat/sessions', () => HttpResponse.json(listSessionStates())),

  http.get('/api/v1/chat/:sessionId', ({ params }) => {
    const session = getSessionState(String(params.sessionId))
    return session ? HttpResponse.json(session) : notFound('Oturum bulunamadı.')
  }),

  http.delete('/api/v1/chat/:sessionId', ({ params }) => {
    const ok = deleteSessionState(String(params.sessionId))
    return ok ? new HttpResponse(null, { status: 204 }) : notFound('Oturum bulunamadı.')
  }),

  // ── Hotel ───────────────────────────────────────────────────────────────
  http.post('/api/v1/hotels/search', async ({ request }) => {
    const criteria = (await request.json()) as HotelSearchCriteria
    const nd = criteria?.destination ? norm(criteria.destination) : ''
    let results = hotelFixtures
    if (nd) {
      const filtered = hotelFixtures.filter(
        (h) => norm(h.region).includes(nd) || nd.includes(norm(h.region)),
      )
      if (filtered.length) results = filtered
    }
    return HttpResponse.json(results)
  }),

  // ── Flight ──────────────────────────────────────────────────────────────
  http.post('/api/v1/flights/search', async ({ request }) => {
    const criteria = (await request.json()) as FlightSearchCriteria
    const nd = criteria?.destination ? norm(criteria.destination) : ''
    let results = flightFixtures
    if (nd) {
      const filtered = flightFixtures.filter((f) => norm(f.destination).includes(nd))
      if (filtered.length) results = filtered
    }
    return HttpResponse.json(results)
  }),

  // ── Reservation ───────────────────────────────────────────────────────────
  http.post('/api/v1/reservations/preview', async ({ request }) => {
    const body = (await request.json()) as CreateReservationRequest
    const product = findProduct(body.productId)
    if (!product) return notFound('Ürün bulunamadı.')
    const preview: ReservationPreview = {
      productType: product.productType,
      productId: body.productId,
      title: product.title,
      summary: product.summary,
      totalAmount: product.price,
      currency: body.currency ?? product.currency,
      passengers: body.passengers ?? [],
    }
    return HttpResponse.json(preview)
  }),

  http.post('/api/v1/reservations', async ({ request }) => {
    const body = (await request.json()) as CreateReservationRequest
    const product = findProduct(body.productId)
    if (!product) return notFound('Ürün bulunamadı.')
    const id = String(nextReservationId++)
    const now = new Date().toISOString()
    const lead = body.passengers?.[0]
    const reservation: Reservation = {
      id,
      reservationNumber: `PAX-MOCK-${id}`,
      productType: product.productType,
      status: 'confirmed',
      reservationDate: now.slice(0, 10),
      totalAmount: product.price,
      currency: body.currency ?? product.currency,
      leadGuestName: lead ? `${lead.firstName} ${lead.lastName}` : undefined,
      createdAt: now,
      updatedAt: now,
      passengers: body.passengers ?? [],
    }
    reservations.unshift(reservation)
    return HttpResponse.json(reservation, { status: 201 })
  }),

  http.get('/api/v1/reservations', () => HttpResponse.json(reservations)),

  http.get('/api/v1/reservations/:id', ({ params }) => {
    const r = reservations.find((x) => x.id === String(params.id))
    return r ? HttpResponse.json(r) : notFound('Rezervasyon bulunamadı.')
  }),

  http.patch('/api/v1/reservations/:id/cancel', ({ params }) => {
    const r = reservations.find((x) => x.id === String(params.id))
    if (!r) return notFound('Rezervasyon bulunamadı.')
    r.status = 'cancelled'
    r.updatedAt = new Date().toISOString()
    return HttpResponse.json(r)
  }),

  // ── Auth (mock; gerçek doğrulama backend'de — şifre burada denetlenmez) ────
  // Durum kodları ve hata gövdeleri backend AuthController/AuthExceptionHandler
  // paritesinde: 201, 400 VALIDATION_ERROR, 409 EMAIL_ALREADY_EXISTS,
  // 401 UNAUTHENTICATED ({ error, message, timestamp }).
  http.post('/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterRequest
    if (!body?.email) {
      return HttpResponse.json(errorBody('VALIDATION_ERROR', 'email: E-posta gerekli.'), {
        status: 400,
      })
    }
    if ((body.password ?? '').length < 8) {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', 'password: Şifre en az 8 karakter olmalıdır.'),
        { status: 400 },
      )
    }
    if (registeredEmails.has(body.email.toLowerCase())) {
      return HttpResponse.json(
        errorBody('EMAIL_ALREADY_EXISTS', `Bu e-posta zaten kayıtlı: ${body.email}`),
        { status: 409 },
      )
    }
    registeredEmails.add(body.email.toLowerCase())
    const user: AuthUser = { id: crypto.randomUUID(), email: body.email, name: body.name }
    const session = issueSession(user)
    writeAuthSession(session)
    return HttpResponse.json(
      { user: session.user, token: session.token, refreshToken: session.refreshToken },
      { status: 201 },
    )
  }),

  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginRequest
    if (!body?.email || !body?.password) {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', 'email/password: E-posta ve şifre gerekli.'),
        { status: 400 },
      )
    }
    const user: AuthUser = {
      id: crypto.randomUUID(),
      email: body.email,
      name: body.email.split('@')[0],
    }
    const session = issueSession(user)
    writeAuthSession(session)
    return HttpResponse.json({
      user: session.user,
      token: session.token,
      refreshToken: session.refreshToken,
    })
  }),

  // Refresh jetonunu doğrulayıp yeni bir çift üretir (rotation — eski refresh jetonu
  // artık geçersiz). Backend paritesi: geçersiz/eşleşmeyen jeton 401 INVALID_REFRESH_TOKEN.
  http.post('/api/v1/auth/refresh', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as { refreshToken?: string } | null
    const session = readAuthSession()
    if (!session || !body?.refreshToken || body.refreshToken !== session.refreshToken) {
      return HttpResponse.json(
        errorBody('INVALID_REFRESH_TOKEN', 'Refresh jetonu geçersiz ya da süresi dolmuş.'),
        { status: 401 },
      )
    }
    const rotated = issueSession(session.user)
    writeAuthSession(rotated)
    return HttpResponse.json({
      user: rotated.user,
      token: rotated.token,
      refreshToken: rotated.refreshToken,
    })
  }),

  http.post('/api/v1/auth/logout', () => {
    writeAuthSession(null)
    return new HttpResponse(null, { status: 204 })
  }),

  // Gerçek backend gibi Authorization: Bearer <token> ister — istemcinin jeton
  // gönderme zinciri (authSlice → setAuthToken → interceptor) mock'ta da doğrulanır.
  http.get('/api/v1/auth/me', ({ request }) => {
    const session = readAuthSession()
    const header = request.headers.get('Authorization')
    if (!session || header !== `Bearer ${session.token}`) {
      return HttpResponse.json(errorBody('UNAUTHENTICATED', 'Oturum yok ya da jeton geçersiz.'), {
        status: 401,
      })
    }
    return HttpResponse.json(session.user)
  }),
]
