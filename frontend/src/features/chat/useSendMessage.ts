import { useEffect, useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { chatApi, type ApiError, type SendMessageResponse } from '@/api'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { assistantReplied, userMessageSent } from '@/features/chat/chatSlice'
import { CHAT_SESSIONS_KEY } from '@/features/chat/useChatSessions'

/**
 * POST /api/v1/chat mutation'ı (docs/frontend-architecture.md §5, §8).
 * `send` kullanıcı mesajını iyimser olarak thread'e yazar ve isteği atar;
 * `retry` son başarısız isteği kullanıcı balonunu ÇOĞALTMADAN yeniden dener.
 * loading/error React Query'den okunur; session id slice'tan gelir.
 *
 * Kullanıcı istek uçuştayken "yeni sohbet"e geçerse (veya başka oturum yüklerse)
 * slice'ın `epoch`'u artar: (1) pending mutation sıfırlanır → yeni sohbette
 * composer kilidi açılır, (2) gelen bayat yanıt yeni thread'e yazılmaz.
 */
export function useSendMessage() {
  const dispatch = useAppDispatch()
  const queryClient = useQueryClient()
  const sessionId = useAppSelector((s) => s.chat.sessionId)
  const epoch = useAppSelector((s) => s.chat.epoch)
  // onSuccess içinde güncel epoch'u okuyabilmek için ref'te tut (istek başlarken
  // bağlanan epoch ile karşılaştırıp konuşma değiştiyse yanıtı yok sayarız).
  const epochRef = useRef(epoch)
  epochRef.current = epoch

  const mutation = useMutation<SendMessageResponse, ApiError, string, { epoch: number }>({
    mutationFn: (message) =>
      chatApi.sendMessage({ sessionId: sessionId ?? undefined, message }),
    onMutate: () => ({ epoch: epochRef.current }),
    onSuccess: (response, _message, context) => {
      // Yeni oturum/başlık/son-mesaj geçmiş panelinde güncellensin (bayat da olsa
      // tamamlanan eski oturumun listelenmesi doğrudur).
      queryClient.invalidateQueries({ queryKey: CHAT_SESSIONS_KEY })
      // İstek başladığından beri kullanıcı başka sohbete geçtiyse yanıtı yazma.
      if (context.epoch !== epochRef.current) return
      dispatch(assistantReplied(response))
    },
  })

  // Sohbet değişiminde uçuştaki isteğin pending/error durumunu temizle —
  // yeni/yüklenen sohbette composer devre dışı kalmasın.
  const { reset } = mutation
  useEffect(() => {
    reset()
  }, [epoch, reset])

  const send = (message: string) => {
    dispatch(userMessageSent(message))
    mutation.mutate(message)
  }

  const retry = () => {
    if (mutation.variables !== undefined) mutation.mutate(mutation.variables)
  }

  return { ...mutation, send, retry }
}
