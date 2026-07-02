import type { IsoDateTime } from './common'
import type { HotelSearchCriteria, FlightSearchCriteria } from './search'
import type { HotelProduct, FlightProduct } from './product'

/**
 * Sohbet domaini (docs/frontend-architecture.md §5, §8). `chatSlice` mesaj thread'ini,
 * biriken arama kriterini (slot-filling) ve bekleyen açık soruyu tutar.
 */

export type ChatRole = 'user' | 'assistant'

/** Asistan mesajına iliştirilen inline sonuç kartı (thread içinde render edilir). */
export type ResultCard =
  | { productType: 'hotel'; product: HotelProduct }
  | { productType: 'flight'; product: FlightProduct }

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  createdAt: IsoDateTime
  /** Yalnızca asistan mesajlarında bulunur; arama sonucu kartları. */
  cards?: ResultCard[]
}

/**
 * Slot-filling boyunca kademeli dolan kriter. `intent` hangi aramanın yapıldığını,
 * `criteria` o ana dek toplanmış (eksik olabilen) alanları taşır.
 */
export type PartialCriteria =
  | { intent: 'hotel'; criteria: Partial<HotelSearchCriteria> }
  | { intent: 'flight'; criteria: Partial<FlightSearchCriteria> }

export interface ChatSession {
  id: string
  messages: ChatMessage[]
  /** Sohbet boyunca biriken arama kriteri (henüz tamamlanmamış olabilir). */
  draftCriteria?: PartialCriteria
  /** Asistanın eksik kriter için sorduğu, yanıt bekleyen açık soru. */
  pendingQuestion?: string
}
