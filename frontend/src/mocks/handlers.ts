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
import { deleteSessionState, getSessionState, norm, processMessage } from './chatEngine'

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

/** Yeni oturum açan kullanıcı (mock). GET /auth/me bunu döner ya da 401. */
let currentUser: AuthUser | null = null

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

  // ── Auth (mock; gerçek doğrulama backend'de) ───────────────────────────────
  http.post('/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterRequest
    currentUser = { id: crypto.randomUUID(), email: body.email, name: body.name }
    return HttpResponse.json(
      { user: currentUser, token: `mock-token-${crypto.randomUUID()}` },
      { status: 201 },
    )
  }),

  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginRequest
    if (!body?.email) return HttpResponse.json({ message: 'E-posta gerekli.' }, { status: 400 })
    currentUser = { id: crypto.randomUUID(), email: body.email, name: body.email.split('@')[0] }
    return HttpResponse.json({ user: currentUser, token: `mock-token-${crypto.randomUUID()}` })
  }),

  http.post('/api/v1/auth/logout', () => {
    currentUser = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/v1/auth/me', () => {
    return currentUser
      ? HttpResponse.json(currentUser)
      : HttpResponse.json({ message: 'Oturum yok.' }, { status: 401 })
  }),
]
