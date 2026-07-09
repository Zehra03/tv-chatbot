import { apiClient } from './client'
import type { ChatMessage, ChatSession, ChatSessionSummary, PartialCriteria } from '@/types'

/**
 * Sohbet endpoint'leri (docs/frontend-architecture.md §6 · §8).
 * Backend sözleşmesi:
 *   POST   /api/v1/chat            → mesaj gönder (intent + slot-filling)
 *   GET    /api/v1/chat/sessions   → oturum listesi (geçmiş paneli)
 *   GET    /api/v1/chat/{id}       → oturumu getir
 *   DELETE /api/v1/chat/{id}       → oturumu sil
 * Chatbot yalnızca arar/listeler/yönlendirir — asla booking yapmaz.
 */

export interface SendMessageRequest {
  /** İlk mesajda boş; backend yeni oturum açar ve id döner. */
  sessionId?: string
  message: string
}

export interface SendMessageResponse {
  sessionId: string
  /** Asistan yanıtı — açıklayıcı soru, `cards` taşıyan sonuç, ya da belirsizlikte
   *  `options` taşıyan seçenekli kart (bkz. ChatMessage.options). */
  reply: ChatMessage
  /** O ana dek toplanmış (eksik olabilen) arama kriterleri. */
  accumulatedCriteria?: PartialCriteria
  /** Eksik kriter varsa yanıt beklenen açık soru; tam kriterde boş. */
  pendingQuestion?: string
}

export const chatApi = {
  async sendMessage(body: SendMessageRequest): Promise<SendMessageResponse> {
    const res = await apiClient.post<SendMessageResponse>('/api/v1/chat', body)
    return res.data
  },

  async listSessions(): Promise<ChatSessionSummary[]> {
    const res = await apiClient.get<ChatSessionSummary[]>('/api/v1/chat/sessions')
    return res.data
  },

  async getSession(sessionId: string): Promise<ChatSession> {
    const res = await apiClient.get<ChatSession>(`/api/v1/chat/${sessionId}`)
    return res.data
  },

  async deleteSession(sessionId: string): Promise<void> {
    await apiClient.delete(`/api/v1/chat/${sessionId}`)
  },
}
