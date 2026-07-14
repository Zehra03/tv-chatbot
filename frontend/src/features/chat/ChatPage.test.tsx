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
import chatReducer from '@/features/chat/chatSlice'
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

function renderChat() {
  const store = configureStore({
    reducer: { auth: authReducer, chat: chatReducer, reservationDraft: reservationDraftReducer },
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
})
