import { describe, expect, it } from 'vitest'
import reducer, { assistantReplied, chatReset, userMessageSent } from './chatSlice'
import type { SendMessageResponse } from '@/api'
import type { ChatMessage } from '@/types'

const reply = (over: Partial<SendMessageResponse> = {}): SendMessageResponse => ({
  sessionId: 'sess-1',
  reply: {
    id: 'm-1',
    role: 'assistant',
    content: 'Hangi şehirde otel arıyorsunuz?',
    createdAt: '2026-07-02T10:00:00Z',
  } satisfies ChatMessage,
  ...over,
})

describe('chatSlice', () => {
  it('userMessageSent kullanıcı mesajını thread sonuna ekler', () => {
    const state = reducer(undefined, userMessageSent('merhaba'))
    expect(state.messages).toHaveLength(1)
    expect(state.messages[0]).toMatchObject({ role: 'user', content: 'merhaba' })
    expect(state.messages[0].id).toBeTruthy()
    expect(state.messages[0].createdAt).toBeTruthy()
  })

  it('assistantReplied session id, yanıt, kriter ve bekleyen soruyu işler', () => {
    const state = reducer(
      undefined,
      assistantReplied(
        reply({
          accumulatedCriteria: { intent: 'hotel', criteria: { destination: 'Antalya' } },
          pendingQuestion: 'Giriş tarihi nedir?',
        }),
      ),
    )
    expect(state.sessionId).toBe('sess-1')
    expect(state.messages).toHaveLength(1)
    expect(state.accumulatedCriteria).toEqual({
      intent: 'hotel',
      criteria: { destination: 'Antalya' },
    })
    expect(state.pendingQuestion).toBe('Giriş tarihi nedir?')
  })

  it('kriter içermeyen yanıt (intent sorusu) birikmiş kriterleri silmez', () => {
    let state = reducer(
      undefined,
      assistantReplied(
        reply({ accumulatedCriteria: { intent: 'hotel', criteria: { destination: 'Antalya' } } }),
      ),
    )
    state = reducer(state, assistantReplied(reply({ pendingQuestion: 'Kaç kişi?' })))
    expect(state.accumulatedCriteria).toEqual({
      intent: 'hotel',
      criteria: { destination: 'Antalya' },
    })
    expect(state.pendingQuestion).toBe('Kaç kişi?')
  })

  it('kriterler tamamlanınca (soru gelmeyince) bekleyen soru temizlenir', () => {
    let state = reducer(undefined, assistantReplied(reply({ pendingQuestion: 'Kaç kişi?' })))
    state = reducer(state, assistantReplied(reply({ pendingQuestion: undefined })))
    expect(state.pendingQuestion).toBeUndefined()
  })

  it('chatReset tüm konuşma durumunu sıfırlar', () => {
    let state = reducer(undefined, userMessageSent('merhaba'))
    state = reducer(state, assistantReplied(reply({ pendingQuestion: 'Kaç kişi?' })))
    state = reducer(state, chatReset())
    expect(state).toEqual({ sessionId: null, messages: [] })
  })
})
