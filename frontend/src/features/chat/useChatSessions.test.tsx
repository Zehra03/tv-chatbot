import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { renderHook, act, cleanup, waitFor } from '@testing-library/react'
import { http, HttpResponse, delay } from 'msw'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { configureStore } from '@reduxjs/toolkit'
import type { ReactNode } from 'react'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import uiReducer from '@/features/ui/uiSlice'
import reservationDraftReducer from '@/features/reservation/reservationDraftSlice'
import { useLoadSession } from '@/features/chat/useChatSessions'

/**
 * Geçmişten oturum yüklerken "son tıklama kazanır". Yanıtlar istek sırasıyla dönmek zorunda
 * değil: yavaş A + hızlı B tıklanırsa, geç dönen A thread'i sessizce ele geçiriyordu.
 */

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const session = (id: string, text: string) => ({
  id,
  messages: [{ id: `m-${id}`, role: 'user' as const, content: text, createdAt: '2026-07-01T10:00:00Z' }],
  accumulatedCriteria: {},
  pendingQuestion: null,
})

function setup() {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      ui: uiReducer,
      reservationDraft: reservationDraftReducer,
    },
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </Provider>
  )
  const { result } = renderHook(() => useLoadSession(), { wrapper })
  return { store, result }
}

describe('useLoadSession — son tıklama kazanır', () => {
  it('geç dönen ESKİ istek, sonra tıklanan oturumun thread’ini ezmez', async () => {
    server.use(
      http.get('*/api/v1/chat/sess-a', async () => {
        await delay(150) // yavaş
        return HttpResponse.json(session('sess-a', 'A oturumu'))
      }),
      http.get('*/api/v1/chat/sess-b', () => HttpResponse.json(session('sess-b', 'B oturumu'))),
    )
    const { store, result } = setup()

    // Kullanıcı önce A'ya, hemen sonra B'ye tıklıyor.
    act(() => {
      result.current.mutate('sess-a')
      result.current.mutate('sess-b')
    })

    // B hızlı döner ve thread'e yerleşir.
    await waitFor(() => expect(store.getState().chat.sessionId).toBe('sess-b'))

    // A geç döner — regresyonda burada thread'i sessizce A ile değiştiriyordu.
    await new Promise((r) => setTimeout(r, 300))
    expect(store.getState().chat.sessionId).toBe('sess-b')
    expect(store.getState().chat.messages[0]?.content).toBe('B oturumu')
  })

  it('tek istekte oturumu normal şekilde yükler', async () => {
    server.use(
      http.get('*/api/v1/chat/sess-a', () => HttpResponse.json(session('sess-a', 'A oturumu'))),
    )
    const { store, result } = setup()

    act(() => {
      result.current.mutate('sess-a')
    })

    await waitFor(() => expect(store.getState().chat.sessionId).toBe('sess-a'))
    expect(store.getState().chat.messages[0]?.content).toBe('A oturumu')
  })
})
