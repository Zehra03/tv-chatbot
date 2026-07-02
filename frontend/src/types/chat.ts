import type { IsoDateTime } from './common'
import type { HotelSearchCriteria, FlightSearchCriteria } from './search'
import type { HotelProduct, FlightProduct } from './product'

/**
 * Sohbet domaini. DB: `chat_sessions` + `chat_messages`. Not: `accumulated_criteria` ve
 * `result_cards` DB'de jsonb'dir (şema iç yapılarını tiplemez) — aşağıdaki
 * `PartialCriteria` / `ResultCard` bu jsonb blob'larının frontend tiplemesidir.
 */

/** DB: chat_messages.role CHECK ('user','assistant','system'). */
export type ChatRole = 'user' | 'assistant' | 'system'

/** `result_cards` (jsonb) içindeki inline sonuç kartı (thread'de render edilir). */
export type ResultCard =
  | { productType: 'hotel'; product: HotelProduct }
  | { productType: 'flight'; product: FlightProduct }

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string // DB: content text
  createdAt: IsoDateTime
  /** DB: result_cards jsonb (asistan mesajlarında dolu olabilir). */
  cards?: ResultCard[]
}

/**
 * `accumulated_criteria` (jsonb) frontend tiplemesi. `intent` hangi aramanın yapıldığını,
 * `criteria` o ana dek toplanmış (eksik olabilen) alanları taşır.
 */
export type PartialCriteria =
  | { intent: 'hotel'; criteria: Partial<HotelSearchCriteria> }
  | { intent: 'flight'; criteria: Partial<FlightSearchCriteria> }

export interface ChatSession {
  id: string
  title?: string // DB: title varchar(200)
  messages: ChatMessage[] // DB: chat_messages (1:N)
  /** DB: accumulated_criteria jsonb (henüz tamamlanmamış olabilir). */
  accumulatedCriteria?: PartialCriteria
  /** Slot-filling'de yanıt bekleyen açık soru (backend in-memory ChatSession.pendingQuestion; transient). */
  pendingQuestion?: string
}
