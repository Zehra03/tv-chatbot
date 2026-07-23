import { http, HttpResponse, type RequestHandler } from 'msw'
import type {
  FlightSearchCriteria,
  HotelSearchCriteria,
  ReservationDetail,
  ReservationSummary,
} from '@/types'
import type {
  AuthUser,
  ConfirmRequest,
  LoginRequest,
  NeedsConfirmationResponse,
  PreviewReservationCommand,
  PreviewResponse,
  RegisterRequest,
  SendMessageRequest,
} from '@/api'
import {
  flightFixtures,
  flightLocationFixtures,
  hotelFixtures,
  hotelLocationFixtures,
  reservationFixtures,
} from './fixtures'
import {
  claimSeedSessions,
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

/** Oluşturulan rezervasyonlar için tohumlanmış, çağrılar arası kalıcı bellek deposu (tam detay). */
const reservations: ReservationDetail[] = reservationFixtures.map((r) => ({
  ...r,
  passengers: r.passengers?.map((p) => ({ ...p })),
}))
let nextReservationId = 2000

/** Aktif önizlemeler (previewId → komut) ve uyarı-sonrası bekleyen commit'ler (token → komut). */
const previewStore = new Map<string, PreviewReservationCommand>()
const pendingCommits = new Map<string, PreviewReservationCommand>()
let nextPreviewSeq = 1

/** Bir komutun ürün tipini hangi blokların dolu olduğundan türetir (backend paritesi). */
function deriveProductType(cmd: PreviewReservationCommand): ReservationDetail['productType'] {
  if (cmd.hotel && cmd.flight) return 'combined'
  return cmd.flight ? 'flight' : 'hotel'
}

/** Test/dev'de uyarı dalını (NeedsUserConfirmation) tetikleyen sinyal: offerId 'OFFER-DUP'. */
function shouldWarn(cmd: PreviewReservationCommand): boolean {
  return (cmd.offerIds ?? []).includes('OFFER-DUP')
}

/**
 * Fiyat değişimi (K21) dalını tetikleyen sinyal: offerId 'OFFER-REPRICE'. Gerçek backend
 * önizlemeyi kurarken TourVisio'nun CANLI fiyatını yeniden okur ve aramadakinden farklıysa
 * priceChanged + previousAmount döndürür. Mock eskiden istemcinin kendi tutarını yankılıyordu,
 * yani bu dal hiç sürülemiyordu — alanların frontend tipinde eksik olduğu da bu yüzden
 * hiçbir testte ortaya çıkmamıştı.
 */
const REPRICE_MULTIPLIER = 1.3
function shouldReprice(cmd: PreviewReservationCommand): boolean {
  return (cmd.offerIds ?? []).includes('OFFER-REPRICE')
}

/** Komuttan tam rezervasyon detayı üretir (onaylı) — çift-jetonlu backend commit'inin karşılığı. */
function reservationFromCommand(cmd: PreviewReservationCommand, id: number): ReservationDetail {
  return {
    id,
    reservationNumber: `PAX-MOCK-${id}`,
    externalReservationNumber: `RC${String(id).padStart(6, '0')}`,
    productType: deriveProductType(cmd),
    status: 'confirmed',
    reservationDate: new Date().toISOString().slice(0, 10),
    totalAmount: cmd.totalAmount,
    currency: cmd.currency,
    leadGuestName: cmd.leadGuestName,
    passengers: (cmd.travellers ?? []).map((t) => ({
      firstName: t.firstName,
      lastName: t.lastName,
      passengerType: t.passengerType,
      age: t.age ?? null,
      nationality: t.nationalityCode ?? null,
      email: t.email ?? null,
      phone: t.phone ?? null,
    })),
    hotel: cmd.hotel ? { ...cmd.hotel } : null,
    flight: cmd.flight ? { ...cmd.flight } : null,
    cancellationOptions: [
      {
        reasonId: 'RSN-USER',
        reasonName: 'Kullanıcı talebi',
        cancelable: true,
        price: { amount: 0, currency: cmd.currency },
        services: [],
      },
    ],
  }
}

/** Özet görünüm — liste satırı / oluşturma yanıtı (detaydan yalnız başlık alanları). */
function toReservationSummary(r: ReservationDetail): ReservationSummary {
  return {
    id: r.id,
    reservationNumber: r.reservationNumber,
    externalReservationNumber: r.externalReservationNumber,
    status: r.status,
    productType: r.productType,
    reservationDate: r.reservationDate,
    totalAmount: r.totalAmount,
    currency: r.currency,
    leadGuestName: r.leadGuestName,
    guest: r.guest ?? false,
  }
}

/** Başarısız/ara sonuç gövdesi (backend OutcomeResponse paritesi). */
const outcomeResponse = (code: string, message: string, status: number) =>
  HttpResponse.json({ outcome: code, message }, { status })

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

/**
 * Chat oturumunun sahibini çözer (gerçek backend ChatController.resolveCaller paritesi):
 * geçerli Bearer jetonu → "user:<id>"; jeton yoksa X-Guest-Id → "guest:<id>"; hiçbiri yoksa
 * "anon" (yalnız kimliksiz/test akışı — gerçek uygulamada /chat her zaman kimlikli). Böylece
 * bir üyenin geçmişi, çıkış yapıp misafir olan çağrana ASLA görünmez.
 */
function resolveChatOwner(request: Request): string {
  const session = readAuthSession()
  const auth = request.headers.get('Authorization')
  if (session && auth === `Bearer ${session.token}`) return `user:${session.user.id}`
  const guestId = request.headers.get('X-Guest-Id')?.trim()
  if (guestId) return `guest:${guestId.slice(0, 64)}`
  return 'anon'
}

/** Backend'in ErrorResponse kaydıyla aynı gövde: { error, message, timestamp }. */
function errorBody(error: string, message: string) {
  return { error, message, timestamp: new Date().toISOString() }
}

/** Kaba e-posta biçim denetimi — backend Bean Validation @Email paritesi. */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const notFound = (message: string) => HttpResponse.json({ message }, { status: 404 })

export const handlers: RequestHandler[] = [
  // ── Chat ────────────────────────────────────────────────────────────────
  http.post('/api/v1/chat', async ({ request }) => {
    const body = (await request.json()) as SendMessageRequest
    return HttpResponse.json(processMessage(body.sessionId, body.message ?? '', resolveChatOwner(request)))
  }),

  // Statik path, ':sessionId' kalıbından ÖNCE kayıtlı olmalı (MSW sıra ile eşler).
  http.get('/api/v1/chat/sessions', ({ request }) =>
    HttpResponse.json(listSessionStates(resolveChatOwner(request))),
  ),

  http.get('/api/v1/chat/:sessionId', ({ params, request }) => {
    const session = getSessionState(String(params.sessionId), resolveChatOwner(request))
    return session ? HttpResponse.json(session) : notFound('Oturum bulunamadı.')
  }),

  http.delete('/api/v1/chat/:sessionId', ({ params, request }) => {
    const ok = deleteSessionState(String(params.sessionId), resolveChatOwner(request))
    return ok ? new HttpResponse(null, { status: 204 }) : notFound('Oturum bulunamadı.')
  }),

  // ── Hotel ───────────────────────────────────────────────────────────────
  // Destination otomatik tamamlama: q'yu şehir adıyla eşleştirir (backend
  // HotelSearchService.suggestLocations paritesi). 2 karakterden kısa sorgu boş döner.
  http.get('/api/v1/hotels/locations', ({ request }) => {
    const q = new URL(request.url).searchParams.get('q') ?? ''
    const needle = norm(q)
    if (needle.length < 2) return HttpResponse.json([])
    const matches = hotelLocationFixtures.filter((l) => norm(l.name).includes(needle))
    return HttpResponse.json(matches.slice(0, 8))
  }),

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
  // Kalkış/varış otomatik tamamlama: q'yu isim/id ile eşleştirir (backend
  // FlightLocationService paritesi). 2 karakterden kısa sorgu boş döner.
  http.get('/api/v1/flights/locations', ({ request }) => {
    const q = new URL(request.url).searchParams.get('q') ?? ''
    const needle = norm(q)
    if (needle.length < 2) return HttpResponse.json([])
    const matches = flightLocationFixtures.filter(
      (l) => norm(l.name).includes(needle) || norm(l.id).includes(needle),
    )
    return HttpResponse.json(matches.slice(0, 8))
  }),

  http.post('/api/v1/flights/search', async ({ request }) => {
    const criteria = (await request.json()) as FlightSearchCriteria
    // Form artık seçilen konumun id/kodunu (ör. "AYT") gönderir; gerçek backend
    // bunu TourVisio konumuna çözer. Mock da id/kod'u fixture konum adına çözer
    // ("AYT" → "Antalya Havalimanı (AYT)") ki şehir bazlı fixture eşleşmesi tutsun.
    // Serbest metin ("Antalya") çözülmez, olduğu gibi kalır — geriye dönük uyumlu.
    const resolveLoc = (value: string) => {
      const loc = flightLocationFixtures.find((l) => l.id === value || l.code === value)
      return norm(loc ? loc.name : value)
    }
    const nd = criteria?.destination ? resolveLoc(criteria.destination) : ''
    let results = flightFixtures
    if (nd) {
      const filtered = flightFixtures.filter(
        (f) => nd.includes(norm(f.destination)) || norm(f.destination).includes(nd),
      )
      if (filtered.length) results = filtered
    }
    return HttpResponse.json(results)
  }),

  // ── Reservation (stateful preview→confirm; backend reservation/ modülü paritesi) ──────────
  // POST /preview: snapshot'ı dondur, previewId üret (yazma/TourVisio yok).
  http.post('/api/v1/reservations/preview', async ({ request }) => {
    const cmd = (await request.json()) as PreviewReservationCommand
    const previewId = `preview-${nextPreviewSeq++}`
    previewStore.set(previewId, cmd)
    // Canlı fiyat: normalde istemcinin gördüğü tutarla aynı; 'OFFER-REPRICE' ile yeniden fiyatlanır.
    const repriced = shouldReprice(cmd)
    const liveAmount = repriced
      ? Math.round(cmd.totalAmount * REPRICE_MULTIPLIER * 100) / 100
      : cmd.totalAmount
    const body: PreviewResponse = {
      previewId,
      expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
      productType: deriveProductType(cmd),
      totalAmount: liveAmount,
      currency: cmd.currency,
      leadGuestName: cmd.leadGuestName,
      passengerNames: (cmd.travellers ?? []).map((t) => `${t.firstName} ${t.lastName}`),
      hasHotel: !!cmd.hotel,
      hasFlight: !!cmd.flight,
      priceChanged: repriced,
      previousAmount: repriced ? cmd.totalAmount : null,
      previousCurrency: repriced ? cmd.currency : null,
      // Önizleme yalnız TourVisio'nun fiyatlamayı kabul ettiği teklif için oluşur.
      available: true,
    }
    return HttpResponse.json(body)
  }),

  // POST /: kesin onay. confirmationToken → uyarı sonrası devam; previewId → normal onay.
  http.post('/api/v1/reservations', async ({ request }) => {
    const body = (await request.json()) as ConfirmRequest

    if (body.confirmationToken) {
      const cmd = pendingCommits.get(body.confirmationToken)
      if (!cmd) return outcomeResponse('PREVIEW_EXPIRED', 'Preview expired, please start again.', 410)
      pendingCommits.delete(body.confirmationToken)
      const created = reservationFromCommand(cmd, nextReservationId++)
      reservations.unshift(created)
      return HttpResponse.json(toReservationSummary(created), { status: 201 })
    }

    const cmd = body.previewId ? previewStore.get(body.previewId) : undefined
    if (!cmd) return outcomeResponse('PREVIEW_EXPIRED', 'Preview expired, please start again.', 410)
    previewStore.delete(body.previewId as string)

    // Uyarı dalı (ör. çift rezervasyon): commit'i beklet, token döndür (200).
    if (shouldWarn(cmd)) {
      const token = `token-${nextPreviewSeq++}`
      pendingCommits.set(token, cmd)
      const warn: NeedsConfirmationResponse = {
        confirmationToken: token,
        warnings: ['Bu ürün için mevcut bir rezervasyon bulundu (DuplicateReservationFound).'],
      }
      return HttpResponse.json(warn, { status: 200 })
    }

    const created = reservationFromCommand(cmd, nextReservationId++)
    reservations.unshift(created)
    return HttpResponse.json(toReservationSummary(created), { status: 201 })
  }),

  http.get('/api/v1/reservations', () => HttpResponse.json(reservations.map(toReservationSummary))),

  http.get('/api/v1/reservations/:id', ({ params }) => {
    const r = reservations.find((x) => x.id === Number(params.id))
    return r ? HttpResponse.json(r) : notFound('Rezervasyon bulunamadı.')
  }),

  http.patch('/api/v1/reservations/:id/cancel', ({ params }) => {
    const r = reservations.find((x) => x.id === Number(params.id))
    if (!r) return notFound('Rezervasyon bulunamadı.')
    r.status = 'cancelled'
    r.cancellationOptions = []
    return outcomeResponse('CANCELLED', `Reservation ${r.id} is now cancelled`, 200)
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
    const user: AuthUser = {
      id: crypto.randomUUID(),
      email: body.email,
      name: body.name,
      role: 'USER',
    }
    const session = issueSession(user)
    writeAuthSession(session)
    claimSeedSessions(`user:${user.id}`)
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
      // Rol HER ZAMAN USER. Bir zamanlar "admin…" ile başlayan e-posta ADMIN sayılıyordu ki
      // panel mock'la gezilebilsin; kaldırıldı — yetki kararını e-posta metninden türetmek,
      // testte bile taklit edilmemesi gereken bir kural. Yönetici gerektiren bir testin rolü
      // kendi içinde açıkça kurması gerekir.
      role: 'USER',
    }
    const session = issueSession(user)
    writeAuthSession(session)
    claimSeedSessions(`user:${user.id}`)
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

  // Şifreyi doğrudan değiştirir (jetonsuz/public) — e-posta bağlantısı yok. Backend
  // paritesi: geçersiz biçim/kısa şifre 400 VALIDATION_ERROR. Mock diğer auth uçları gibi
  // gerçek kimliği denetlemez, bu yüzden geçerli girdide başarı döner; 404 EMAIL_NOT_FOUND
  // gerçek-backend davranışıdır ve testlerde server.use override ile ele alınır.
  http.post('/api/v1/auth/reset-password', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as {
      email?: string
      password?: string
    } | null
    const email = (body?.email ?? '').trim()
    const password = body?.password ?? ''
    if (!EMAIL_RE.test(email)) {
      return HttpResponse.json(errorBody('VALIDATION_ERROR', 'email: Geçerli bir e-posta girin.'), {
        status: 400,
      })
    }
    if (password.length < 8) {
      return HttpResponse.json(
        errorBody('VALIDATION_ERROR', 'password: Şifre en az 8 karakter olmalıdır.'),
        { status: 400 },
      )
    }
    return HttpResponse.json({
      message: 'Şifreniz güncellendi. Yeni şifrenle giriş yapabilirsin.',
    })
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

  // Oturumdaki kullanıcının e-postasını günceller. GET /me ile aynı jeton denetimi;
  // geçersiz biçim 400, başka bir kayıtlı e-postayla çakışma 409 (register paritesi).
  // Başarıda oturumu günceller ve güncel AuthUser döner.
  http.patch('/api/v1/auth/me', async ({ request }) => {
    const session = readAuthSession()
    const header = request.headers.get('Authorization')
    if (!session || header !== `Bearer ${session.token}`) {
      return HttpResponse.json(errorBody('UNAUTHENTICATED', 'Oturum yok ya da jeton geçersiz.'), {
        status: 401,
      })
    }
    const body = (await request.json().catch(() => null)) as { email?: string } | null
    const email = (body?.email ?? '').trim()
    if (!EMAIL_RE.test(email)) {
      return HttpResponse.json(errorBody('VALIDATION_ERROR', 'email: Geçerli bir e-posta girin.'), {
        status: 400,
      })
    }
    const current = session.user.email.toLowerCase()
    const next = email.toLowerCase()
    if (next !== current && registeredEmails.has(next)) {
      return HttpResponse.json(
        errorBody('EMAIL_ALREADY_EXISTS', `Bu e-posta zaten kayıtlı: ${email}`),
        { status: 409 },
      )
    }
    // Kayıtlı e-posta kümesini de taşı ki sonraki çakışma denetimleri tutarlı kalsın.
    registeredEmails.delete(current)
    registeredEmails.add(next)
    const updated: AuthUser = { ...session.user, email }
    writeAuthSession({ ...session, user: updated })
    return HttpResponse.json(updated)
  }),
]
