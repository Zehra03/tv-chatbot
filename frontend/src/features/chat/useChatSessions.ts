import { useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { chatApi, type ApiError } from '@/api'
import type { ChatSession, ChatSessionSummary } from '@/types'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectIdentity } from '@/features/auth/selectors'
import { chatReset, sessionLoaded } from '@/features/chat/chatSlice'

/** Geçmiş paneli query key ön-eki — invalidate/remove bu ön-ekle tüm kimlik
 * varyantlarını (kısmi eşleşme) kapsar; useSendMessage başarıda invalidate eder. */
export const CHAT_SESSIONS_KEY = ['chat', 'sessions'] as const

/**
 * GET /api/v1/chat/sessions — oturum özet listesi (geçmiş paneli).
 * Query key aktif kimliği (üye id'si ya da misafir kimliği) taşır: bir hesabın
 * cache'lenmiş geçmişi, çıkış yapıp misafir/başka hesap olarak devam edene ASLA
 * gösterilmez — ayrı kimlik = ayrı cache girdisi.
 */
export function useChatSessions() {
  const identity = useAppSelector(selectIdentity)
  return useQuery<ChatSessionSummary[], ApiError>({
    queryKey: [...CHAT_SESSIONS_KEY, identity ?? 'anon'],
    queryFn: () => chatApi.listSessions(),
  })
}

/**
 * GET /api/v1/chat/{id} — seçilen oturumu getirir ve thread'e yükler.
 *
 * Son-tıklama kazanır: yanıtlar istek sırasıyla dönmek zorunda değil. Kullanıcı yavaş A'ya,
 * hemen ardından hızlı B'ye tıklarsa B önce dönüp thread'i doldurur; sonra A dönüp thread'i,
 * biriken kriterleri ve bekleyen soruyu SESSİZCE A ile değiştiriyordu — kullanıcı B'ye
 * tıklamışken A'yı okuyordu (kenar çubuğu vurgusu da A'ya kayıyor, yani hata gizli kalıyordu).
 *
 * useSendMessage'in `epoch` deseni burada YETMEZ: epoch "konuşma değişti mi"yi yanıtlar, "hangi
 * tıklama en yenisi"ni değil — A önce dönerse epoch artar ve kullanıcının asıl istediği B'nin
 * yanıtı atılırdı. Bu yüzden ölçüt istenen sessionId'nin kendisi.
 */
export function useLoadSession() {
  const dispatch = useAppDispatch()
  const latestRequestRef = useRef<string | null>(null)
  return useMutation<ChatSession, ApiError, string>({
    mutationFn: (sessionId) => chatApi.getSession(sessionId),
    onMutate: (sessionId) => {
      latestRequestRef.current = sessionId
    },
    onSuccess: (session, sessionId) => {
      // Bu yanıt en son tıklanan oturuma ait değilse yok say.
      if (latestRequestRef.current !== sessionId) return
      dispatch(sessionLoaded(session))
    },
  })
}

/** DELETE /api/v1/chat/{id} — oturumu siler; aktif oturumsa thread'i sıfırlar. */
export function useDeleteSession() {
  const dispatch = useAppDispatch()
  const queryClient = useQueryClient()
  const activeSessionId = useAppSelector((s) => s.chat.sessionId)
  return useMutation<void, ApiError, string>({
    mutationFn: (sessionId) => chatApi.deleteSession(sessionId),
    onSuccess: (_data, sessionId) => {
      if (sessionId === activeSessionId) dispatch(chatReset())
      queryClient.invalidateQueries({ queryKey: CHAT_SESSIONS_KEY })
    },
  })
}
