import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { webcrypto } from 'node:crypto'
import { delay, http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer, { type ChatState } from '@/features/chat/chatSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { ChatPage } from '@/features/chat/ChatPage'

/**
 * Uçtan uca chat akışı — gerçek chatApi + MSW node sunucusu (mock chatEngine)
 * üzerinden: slot-filling soruları, kriter rozetleri, sonuç kartları ve
 * Seç → reservationDraft → /reservation/new yönlendirmesi.
 */

// jsdom crypto.randomUUID sağlamaz; mock chatEngine kullanır.
beforeAll(() => {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
  }
  server.listen({ onUnhandledRequest: 'error' })
})
// Vitest globals kapalı olduğundan RTL auto-cleanup çalışmaz — elle temizle.
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

function renderChat(chatState?: ChatState) {
  const store = configureStore({
    reducer: { auth: authReducer, chat: chatReducer, reservationDraft: reservationDraftReducer },
    ...(chatState ? { preloadedState: { chat: chatState } } : {}),
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/chat']}>
          <Routes>
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/reservation/new" element={<div>REZERVASYON FORMU STUB</div>} />
            <Route path="/reservations" element={<div>REZERVASYONLARIM STUB</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return { store }
}

async function send(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(screen.getByLabelText('Mesaj'), text)
  await user.click(screen.getByRole('button', { name: /gönder/i }))
}

describe('ChatPage (MSW ile uçtan uca)', () => {
  it('eksik kriterde asistan açıklayıcı soru sorar; biriken kriterler rozetlerde korunur', async () => {
    const user = userEvent.setup()
    renderChat()

    // Intent belirsiz → asistan intent sorusu sorar; kullanıcı mesajı thread'de.
    await send(user, 'merhaba')
    expect(await screen.findByText('merhaba')).toBeTruthy()
    expect(
      await screen.findByText('Otel araması mı yoksa uçuş araması mı yapmak istersiniz?', {}, { timeout: 3000 }),
    ).toBeTruthy()

    // Intent + şehir verilince sıradaki eksik kriter sorulur, rozetler birikir.
    await send(user, 'Antalya oteli istiyorum')
    expect(await screen.findByText('Giriş tarihi nedir? (örn. 2026-08-01)', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('Otel araması')).toBeTruthy()
    expect(screen.getByText('Nereye: Antalya')).toBeTruthy()
  })

  it('belirsiz girdide seçenekli kart çıkar; "Otel ara"ya tıklamak şehri koruyarak otel akışını sürdürür', async () => {
    const user = userEvent.setup()
    renderChat()

    // Sadece şehir adı → intent belirsiz → asistan seçenekli kart gösterir.
    await send(user, 'Antalya')
    expect(
      await screen.findByText('Otel araması mı yoksa uçuş araması mı yapmak istersiniz?', {}, { timeout: 3000 }),
    ).toBeTruthy()
    const otelAra = await screen.findByRole('button', { name: 'Otel ara' })
    expect(screen.getByRole('button', { name: 'Uçuş ara' })).toBeTruthy()

    // "Otel ara" → "Antalya için otel arıyorum" yeni tur olarak gider: otel akışı, şehir korunur.
    await user.click(otelAra)
    expect(await screen.findByText('Giriş tarihi nedir? (örn. 2026-08-01)', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('Otel araması')).toBeTruthy() // kriter rozeti (kart butonu değil)
    expect(screen.getByText('Nereye: Antalya')).toBeTruthy()
  })

  it('hızlı-eylem kartı ("Otel ara") doğal cümle balonu yazar ve otel akışını başlatır', async () => {
    const user = userEvent.setup()
    renderChat()

    // Kart tıklanınca ham "/otel" değil doğal cümle kullanıcı balonunda görünür...
    await user.click(screen.getByRole('button', { name: 'Otel ara' }))
    expect(await screen.findByText('Otel aramak istiyorum')).toBeTruthy()
    // ...ve doğru intent (otel) tetiklenir → slot-filling ilk soruyu sorar.
    expect(
      await screen.findByText('Hangi şehir veya bölgede otel arıyorsunuz?', {}, { timeout: 3000 }),
    ).toBeTruthy()
  })

  it('"Rezervasyonlarım" kartı sohbete mesaj yazmadan /reservations sayfasına yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderChat()

    await user.click(screen.getByRole('button', { name: 'Rezervasyonlarım' }))
    expect(await screen.findByText('REZERVASYONLARIM STUB')).toBeTruthy()
    // Sohbete kullanıcı balonu eklenmez (chatbot listeler/yönlendirir, mesaj yazmaz).
    expect(store.getState().chat.messages).toHaveLength(0)
  })

  it('elle yazılan "/ucus" komutu backend\'e ham gitmez; doğal cümleye çevrilip uçuş akışına girer', async () => {
    const user = userEvent.setup()
    renderChat()

    await send(user, '/ucus')
    expect(await screen.findByText('Uçuş aramak istiyorum')).toBeTruthy()
    expect(
      await screen.findByText('Nereden kalkış yapacaksınız?', {}, { timeout: 3000 }),
    ).toBeTruthy()
  })

  it('mesaj gönderilip yanıt gelince imleç sohbet input\'una geri döner (odak korunur)', async () => {
    const user = userEvent.setup()
    renderChat()

    const input = () => screen.getByLabelText('Mesaj') as HTMLTextAreaElement
    // Gönder'e tıklamak odağı butona kaçırır ve istek pending iken input disabled olur.
    await send(user, 'merhaba')

    // Yanıt gelip composer tekrar etkinleşince odak input'a geri döner —
    // kullanıcı yeniden tıklamadan yazmaya devam edebilir (madde 11).
    await screen.findByText(
      'Otel araması mı yoksa uçuş araması mı yapmak istersiniz?',
      {},
      { timeout: 3000 },
    )
    await waitFor(() => expect(document.activeElement).toBe(input()))
  })

  it('istek uçuştayken "Yeni sohbet"e geçilince composer yeniden yazılabilir olur', async () => {
    const user = userEvent.setup()
    // İlk yanıtı askıda tut: sessionId hâlâ null iken yeni sohbet senaryosu —
    // regresyon: eskiden pending mutation sıfırlanmadığı için composer kilitli kalıyordu.
    server.use(
      http.post('/api/v1/chat', async () => {
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )
    renderChat()

    await send(user, 'Antalya oteli')
    const input = () => screen.getByLabelText('Mesaj') as HTMLTextAreaElement
    // İstek beklerken composer kilitli.
    await waitFor(() => expect(input().disabled).toBe(true))

    // Yeni sohbet → bekleyen istek geçersizleşir, composer tekrar açılır.
    await user.click(screen.getByRole('button', { name: /yeni sohbet/i }))
    await waitFor(() => expect(input().disabled).toBe(false))
  })

  it('tam kriterde sonuç paneli + "Sonuçları göster" özeti çıkar; Seç forma yönlendirir', async () => {
    const user = userEvent.setup()
    const { store } = renderChat()

    await send(user, 'Antalya otel 2026-08-01 2026-08-05 2 kişi')

    // Kriterler tamam → sağ sonuç panelinde fixture kartı; thread'de özet düğmesi
    // (kartlar artık balonlara yığılmıyor — madde 8).
    expect(await screen.findByText('MOCK Grand Antalya Resort', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByRole('button', { name: /sonuçları göster/i })).toBeTruthy()

    // Seç → reservationDraft dolar, kontrollü forma yönlendirilir (booking yok).
    await user.click(screen.getByRole('button', { name: /seç/i }))
    expect(await screen.findByText('REZERVASYON FORMU STUB')).toBeTruthy()
    expect(store.getState().reservationDraft.draft).toMatchObject({
      productType: 'hotel',
      offerId: 'off-htl-mock-001',
      title: 'MOCK Grand Antalya Resort',
      currency: 'EUR',
    })
  })

  // İsteği süresiz askıda tutan handler: gösterge, yanıt gelmeden pending kalır —
  // böylece "Arıyorum…" eşiğini (700ms) sabit bir yanıt süresiyle yarıştırmadan,
  // yalnız gerçek aramada mı çıkıyor / normal sohbette çıkmıyor mu sınayabiliriz.
  const hangChat = () =>
    server.use(
      http.post('/api/v1/chat', async () => {
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )

  // TypingIndicator kökü role="status" taşır; metnini textContent'ten okuruz
  // (varsayılan/"yazıyor" metni sr-only olduğundan findByText yerine bu sağlam).
  const indicatorText = () =>
    screen
      .queryAllByRole('status')
      .map((n) => n.textContent ?? '')
      .join(' ')

  // Ardışık iki tur yazmak yerine sohbet durumunu önceden yükleriz: tek gönderim,
  // arama-turu çıkarımını (isSearchTurn) besleyen kriter/pendingQuestion sabit kalır.
  const msgs = (): ChatState['messages'] => [
    { id: 'm1', role: 'user', content: 'Antalya oteli', createdAt: '2026-07-15T10:00:00Z' },
    { id: 'm2', role: 'assistant', content: 'Devam edelim.', createdAt: '2026-07-15T10:00:01Z' },
  ]

  it('yavaş bir slot-filling turunda "Arıyorum…" GÖSTERMEZ — yalnız normal yazıyor göstergesi', async () => {
    const user = userEvent.setup()
    // Kriter eksik (yalnız şehir) + asistan hâlâ soruyor → bu tur bir arama DEĞİL.
    renderChat({
      sessionId: 'sess-1',
      epoch: 0,
      messages: msgs(),
      accumulatedCriteria: { intent: 'hotel', criteria: { destination: 'Antalya' } },
      pendingQuestion: 'Giriş tarihi nedir?',
    })

    hangChat()
    await send(user, 'henüz emin değilim')

    // Eşik (700ms) geçtikten SONRA bile — istek hâlâ askıda — gösterge "Arıyorum…"a
    // dönmez, çünkü kriterler eksik = arama değil (normal sohbet).
    await new Promise((r) => setTimeout(r, 1100))
    expect(indicatorText()).toContain('Asistan yazıyor')
    expect(indicatorText()).not.toContain('Arıyorum')
  })

  it('yavaş bir GERÇEK aramada "Arıyorum…" gösterir', async () => {
    const user = userEvent.setup()
    // Kriterler tam (destination + tarihler + kişi) → bu tur bir (yeniden) arama.
    renderChat({
      sessionId: 'sess-2',
      epoch: 0,
      messages: msgs(),
      accumulatedCriteria: {
        intent: 'hotel',
        criteria: { destination: 'Antalya', checkIn: '2026-08-01', checkOut: '2026-08-05', adults: 2 },
      },
    })

    hangChat()
    await send(user, 'sadece 5 yıldız olsun')
    // Eşik geçilince gösterge "Arıyorum…"a döner (kriterler tam = arama).
    expect(await screen.findByText('Arıyorum…', {}, { timeout: 2000 })).toBeTruthy()
  })
})
