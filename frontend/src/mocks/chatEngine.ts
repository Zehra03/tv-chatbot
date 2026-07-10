import type {
  ChatMessage,
  ChatRole,
  ChatSession,
  ChatSessionSummary,
  FlightSearchCriteria,
  HotelSearchCriteria,
  PartialCriteria,
  ResultCard,
} from '@/types'
import type { SendMessageResponse } from '@/api/chatApi'
import { flightFixtures, hotelFixtures } from './fixtures'

/**
 * ⚠️ MOCK sohbet motoru — backend intent-extraction + slot-filling'in KABA bir
 * taklidi (docs/frontend-architecture.md §8). Gerçek NLU değildir: anahtar
 * kelime + regex ile intent'i (otel/uçuş) çıkarır ve eksik kriterleri sırayla
 * sorar. Tüm kriterler dolunca fixture kartları döner. Oturum durumu yalnızca
 * bellekte tutulur (sayfa yenilenince sıfırlanır).
 */

type Intent = 'hotel' | 'flight'

interface SessionState {
  id: string
  title?: string
  messages: ChatMessage[]
  intent?: Intent
  hotel: Partial<HotelSearchCriteria>
  flight: Partial<FlightSearchCriteria>
  pendingQuestion?: string
  /** Son mesajın zamanı — geçmiş paneli bununla sıralanır. */
  updatedAt: string
}

const sessions = new Map<string, SessionState>()

/** Türkçe'yi ASCII'ye indirger (İ/ı/ş/ğ/ü/ö/ç) — eşleştirmeyi sağlamlaştırır. */
export function norm(s: string): string {
  return s
    .toLowerCase()
    .replace(/i̇/g, 'i')
    .replace(/ı/g, 'i')
    .replace(/ş/g, 's')
    .replace(/ğ/g, 'g')
    .replace(/ü/g, 'u')
    .replace(/ö/g, 'o')
    .replace(/ç/g, 'c')
}

/** norm() edilmiş anahtar → görüntülenecek kanonik şehir adı. */
const CITY_CANON: Record<string, string> = {
  antalya: 'Antalya',
  istanbul: 'İstanbul',
  izmir: 'İzmir',
  ankara: 'Ankara',
  bodrum: 'Bodrum',
  kapadokya: 'Kapadokya',
  nevsehir: 'Nevşehir',
  londra: 'Londra',
  paris: 'Paris',
  berlin: 'Berlin',
  roma: 'Roma',
  amsterdam: 'Amsterdam',
}

function extractCities(text: string): string[] {
  const n = norm(text)
  const hits: Array<{ city: string; at: number }> = []
  for (const key of Object.keys(CITY_CANON)) {
    const at = n.indexOf(key)
    if (at >= 0) hits.push({ city: CITY_CANON[key], at })
  }
  return hits.sort((a, b) => a.at - b.at).map((h) => h.city)
}

function extractDates(text: string): string[] {
  const out: string[] = []
  const iso = text.match(/\d{4}-\d{2}-\d{2}/g)
  if (iso) out.push(...iso)
  const dmy = text.match(/\b\d{1,2}[./]\d{1,2}[./]\d{4}\b/g)
  if (dmy) {
    for (const token of dmy) {
      const m = token.match(/(\d{1,2})[./](\d{1,2})[./](\d{4})/)
      if (m) out.push(`${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`)
    }
  }
  return out
}

function extractCount(text: string): number | undefined {
  const m = norm(text).match(/(\d+)\s*(kisi|yolcu|yetiskin|adult|passenger|pax)/)
  return m ? parseInt(m[1], 10) : undefined
}

function detectIntent(text: string): Intent | undefined {
  const n = norm(text)
  if (/(ucus|ucak|flight|bilet|kalkis)/.test(n)) return 'flight'
  if (/(otel|hotel|konaklama|tatil|oda)/.test(n)) return 'hotel'
  return undefined
}

function fillHotel(h: Partial<HotelSearchCriteria>, msg: string): void {
  const cities = extractCities(msg)
  if (!h.destination && cities.length) h.destination = cities[0]
  for (const d of extractDates(msg)) {
    if (!h.checkIn) h.checkIn = d
    else if (!h.checkOut && d !== h.checkIn) h.checkOut = d
  }
  const count = extractCount(msg)
  if (count && !h.adults) h.adults = count
}

function fillFlight(f: Partial<FlightSearchCriteria>, msg: string): void {
  for (const city of extractCities(msg)) {
    if (!f.origin) f.origin = city
    else if (!f.destination && city !== f.origin) f.destination = city
  }
  const dates = extractDates(msg)
  if (dates.length && !f.departDate) f.departDate = dates[0]
  const count = extractCount(msg)
  if (count && !f.passengers) f.passengers = count
}

const HOTEL_QUESTIONS: Array<[keyof HotelSearchCriteria, string]> = [
  ['destination', 'Hangi şehir veya bölgede otel arıyorsunuz?'],
  ['checkIn', 'Giriş tarihi nedir? (örn. 2026-08-01)'],
  ['checkOut', 'Çıkış tarihi nedir? (örn. 2026-08-05)'],
  ['adults', 'Kaç yetişkin için arama yapayım?'],
]

const FLIGHT_QUESTIONS: Array<[keyof FlightSearchCriteria, string]> = [
  ['origin', 'Nereden kalkış yapacaksınız?'],
  ['destination', 'Nereye gitmek istiyorsunuz?'],
  ['departDate', 'Gidiş tarihi nedir? (örn. 2026-08-01)'],
  ['passengers', 'Kaç yolcu için arama yapayım?'],
]

function isEmpty(v: unknown): boolean {
  return v === undefined || v === null || v === ''
}

function firstMissing<T>(questions: Array<[keyof T, string]>, obj: Partial<T>): string | undefined {
  for (const [key, q] of questions) {
    if (isEmpty(obj[key])) return q
  }
  return undefined
}

function hotelCards(destination?: string): ResultCard[] {
  const nd = destination ? norm(destination) : ''
  let list = hotelFixtures
  if (nd) {
    const filtered = hotelFixtures.filter(
      (h) => norm(h.region).includes(nd) || nd.includes(norm(h.region)),
    )
    if (filtered.length) list = filtered
  }
  return list.map((product) => ({ productType: 'hotel', product }))
}

function flightCards(origin?: string, destination?: string): ResultCard[] {
  const nd = destination ? norm(destination) : ''
  const no = origin ? norm(origin) : ''
  let list = flightFixtures
  if (nd || no) {
    const filtered = flightFixtures.filter(
      (f) =>
        (!nd || norm(f.destination).includes(nd)) && (!no || norm(f.origin).includes(no)),
    )
    if (filtered.length) list = filtered
  }
  return list.map((product) => ({ productType: 'flight', product }))
}

function buildCriteria(s: SessionState): PartialCriteria | undefined {
  if (s.intent === 'hotel') return { intent: 'hotel', criteria: s.hotel }
  if (s.intent === 'flight') return { intent: 'flight', criteria: s.flight }
  return undefined
}

function makeMessage(role: ChatRole, content: string, cards?: ResultCard[]): ChatMessage {
  const msg: ChatMessage = {
    id: crypto.randomUUID(),
    role,
    content,
    createdAt: new Date().toISOString(),
  }
  if (cards) msg.cards = cards
  return msg
}

/**
 * Bir kullanıcı mesajını işler: intent yoksa sorar, eksik kriter varsa açıklayıcı
 * soru sorar, tüm kriter dolunca kart seti döner. Chatbot ASLA booking yapmaz —
 * yalnızca listeler ve rezervasyon formuna yönlendirir.
 */
export function processMessage(
  sessionId: string | undefined,
  message: string,
): SendMessageResponse {
  let s = sessionId ? sessions.get(sessionId) : undefined
  if (!s) {
    s = {
      id: crypto.randomUUID(),
      messages: [],
      hotel: {},
      flight: {},
      updatedAt: new Date().toISOString(),
    }
    sessions.set(s.id, s)
  }
  s.messages.push(makeMessage('user', message))
  // Başlık ilk kullanıcı mesajından türer (backend'de title varchar(200)).
  if (!s.title) s.title = message.length > 48 ? `${message.slice(0, 48)}…` : message
  s.updatedAt = new Date().toISOString()

  if (!s.intent) s.intent = detectIntent(message)

  // 1) Intent belirsiz → seçenekli kart ile netleştir ("hangisini demek istediniz?").
  //    Seçenek değeri orijinal mesajı taşır → tıklanınca şehir vb. bir sonraki tura aktarılır.
  //    (Gerçek backend'de bu, AMBIGUOUS intent + OrchestrationResult.choices ile üretilir.)
  if (!s.intent) {
    const q = 'Otel araması mı yoksa uçuş araması mı yapmak istersiniz?'
    s.pendingQuestion = q
    const reply = makeMessage('assistant', q)
    reply.options = [
      { label: 'Otel ara', value: `${message} için otel arıyorum` },
      { label: 'Uçuş ara', value: `${message} için uçuş arıyorum` },
    ]
    s.messages.push(reply)
    return { sessionId: s.id, reply, pendingQuestion: q }
  }

  // 2) Bu mesajdan çıkarılabilen slotları doldur.
  if (s.intent === 'hotel') fillHotel(s.hotel, message)
  else fillFlight(s.flight, message)

  const criteria = buildCriteria(s)
  const missing =
    s.intent === 'hotel'
      ? firstMissing<HotelSearchCriteria>(HOTEL_QUESTIONS, s.hotel)
      : firstMissing<FlightSearchCriteria>(FLIGHT_QUESTIONS, s.flight)

  // 3) Eksik kriter → açıklayıcı soru.
  if (missing) {
    s.pendingQuestion = missing
    const reply = makeMessage('assistant', missing)
    s.messages.push(reply)
    return { sessionId: s.id, reply, accumulatedCriteria: criteria, pendingQuestion: missing }
  }

  // 4) Tüm kriter dolu → kart seti.
  s.pendingQuestion = undefined
  const cards =
    s.intent === 'hotel'
      ? hotelCards(s.hotel.destination)
      : flightCards(s.flight.origin, s.flight.destination)
  const where = s.intent === 'hotel' ? s.hotel.destination : s.flight.destination
  const reply = makeMessage(
    'assistant',
    `"${where}" için ${cards.length} sonuç buldum. Rezervasyon için bir kart seçin — ` +
      `sohbet yalnızca listeler, booking'i onay formu yapar.`,
    cards,
  )
  s.messages.push(reply)
  return { sessionId: s.id, reply, accumulatedCriteria: criteria }
}

export function getSessionState(sessionId: string): ChatSession | undefined {
  const s = sessions.get(sessionId)
  if (!s) return undefined
  return {
    id: s.id,
    title: s.title,
    messages: s.messages,
    accumulatedCriteria: buildCriteria(s),
    pendingQuestion: s.pendingQuestion,
  }
}

export function deleteSessionState(sessionId: string): boolean {
  return sessions.delete(sessionId)
}

/** Geçmiş paneli için oturum özetleri — en son güncellenen üstte. */
export function listSessionStates(): ChatSessionSummary[] {
  return [...sessions.values()]
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .map((s) => ({
      id: s.id,
      title: s.title,
      updatedAt: s.updatedAt,
      messageCount: s.messages.length,
    }))
}

/**
 * ⚠️ MOCK tohum oturumları — geçmiş paneli ilk açılışta boş görünmesin diye.
 * Sabit id/timestamp kullanılır (modül yüklenirken crypto.randomUUID jsdom'da
 * henüz polyfill'lenmemiş olabilir). Devam ettirilebilirler: intent + kriterler dolu.
 */
function seedSession(state: SessionState): void {
  sessions.set(state.id, state)
}

seedSession({
  id: 'seed-session-hotel',
  title: 'Kapadokya balayı oteli',
  intent: 'hotel',
  hotel: { destination: 'Kapadokya', checkIn: '2026-07-10', checkOut: '2026-07-14', adults: 2 },
  flight: {},
  updatedAt: '2026-07-01T18:42:00.000Z',
  messages: [
    {
      id: 'seed-hotel-msg-1',
      role: 'user',
      content: 'Kapadokya balayı oteli arıyorum, 2026-07-10 / 2026-07-14 arası 2 kişi',
      createdAt: '2026-07-01T18:41:00.000Z',
    },
    {
      id: 'seed-hotel-msg-2',
      role: 'assistant',
      content:
        '"Kapadokya" için sonuçları buldum. Rezervasyon için bir kart seçin — ' +
        "sohbet yalnızca listeler, booking'i onay formu yapar.",
      createdAt: '2026-07-01T18:42:00.000Z',
      cards: hotelCards('Kapadokya'),
    },
  ],
})

seedSession({
  id: 'seed-session-flight',
  title: 'İstanbul - Roma uçuşu',
  intent: 'flight',
  hotel: {},
  flight: { origin: 'İstanbul', destination: 'Roma' },
  pendingQuestion: 'Gidiş tarihi nedir? (örn. 2026-08-01)',
  updatedAt: '2026-06-28T09:15:00.000Z',
  messages: [
    {
      id: 'seed-flight-msg-1',
      role: 'user',
      content: "İstanbul'dan Roma'ya uçuş bakar mısın?",
      createdAt: '2026-06-28T09:14:00.000Z',
    },
    {
      id: 'seed-flight-msg-2',
      role: 'assistant',
      content: 'Gidiş tarihi nedir? (örn. 2026-08-01)',
      createdAt: '2026-06-28T09:15:00.000Z',
    },
  ],
})
