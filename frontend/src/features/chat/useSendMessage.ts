import { useMutation } from '@tanstack/react-query'
import { chatApi, type ApiError, type SendMessageResponse } from '@/api'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { assistantReplied, userMessageSent } from '@/features/chat/chatSlice'

/**
 * POST /api/v1/chat mutation'ı (docs/frontend-architecture.md §5, §8).
 * Kullanıcı mesajı iyimser olarak thread'e yazılır (onMutate), asistan yanıtı
 * gelince chatSlice'a işlenir. loading/error React Query'den okunur —
 * `mutate` mesaj metnini alır; session id slice'tan gelir.
 */
export function useSendMessage() {
  const dispatch = useAppDispatch()
  const sessionId = useAppSelector((s) => s.chat.sessionId)

  return useMutation<SendMessageResponse, ApiError, string>({
    mutationFn: (message) =>
      chatApi.sendMessage({ sessionId: sessionId ?? undefined, message }),
    onMutate: (message) => {
      dispatch(userMessageSent(message))
    },
    onSuccess: (response) => {
      dispatch(assistantReplied(response))
    },
  })
}
