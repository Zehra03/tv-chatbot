import { createSlice, nanoid, type PayloadAction } from '@reduxjs/toolkit'
import type { ChatMessage, PartialCriteria } from '@/types'
import type { SendMessageResponse } from '@/api'

/**
 * Sohbet UI state'i (docs/frontend-architecture.md §5): mesaj thread'i, aktif
 * session id, chatbot'un kademeli doldurduğu arama kriterleri (slot-filling)
 * ve yanıt bekleyen açıklayıcı soru. Sunucu çağrısının loading/error'u burada
 * DEĞİL React Query'dedir (useSendMessage); burada yalnızca konuşma durumu yaşar.
 */
export interface ChatState {
  sessionId: string | null
  messages: ChatMessage[]
  /** O ana dek biriken (eksik olabilen) arama kriterleri. */
  accumulatedCriteria?: PartialCriteria
  /** Asistanın yanıt beklediği açık soru; kriterler tamamlanınca temizlenir. */
  pendingQuestion?: string
}

const initialState: ChatState = {
  sessionId: null,
  messages: [],
}

const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {
    /** Kullanıcı mesajını iyimser (istek beklenmeden) thread'e ekler. */
    userMessageSent: {
      reducer(state, action: PayloadAction<ChatMessage>) {
        state.messages.push(action.payload)
      },
      prepare(content: string) {
        return {
          payload: {
            id: nanoid(),
            role: 'user',
            content,
            createdAt: new Date().toISOString(),
          } satisfies ChatMessage,
        }
      },
    },
    /** Backend yanıtını işler: session'ı sabitler, yanıtı thread'e ekler,
     * biriken kriterleri ve bekleyen soruyu günceller. Kriter içermeyen yanıt
     * (ör. intent sorusu) o ana dek birikeni SİLMEZ. */
    assistantReplied(state, action: PayloadAction<SendMessageResponse>) {
      const { sessionId, reply, accumulatedCriteria, pendingQuestion } = action.payload
      state.sessionId = sessionId
      state.messages.push(reply)
      if (accumulatedCriteria) state.accumulatedCriteria = accumulatedCriteria
      state.pendingQuestion = pendingQuestion
    },
    /** Yeni sohbet — tüm konuşma durumunu sıfırlar. */
    chatReset() {
      return initialState
    },
  },
})

export const { userMessageSent, assistantReplied, chatReset } = chatSlice.actions
export default chatSlice.reducer
