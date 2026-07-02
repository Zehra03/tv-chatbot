import { useMutation } from '@tanstack/react-query'
import { chatApi, type ApiError, type SendMessageResponse } from '@/api'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { assistantReplied, userMessageSent } from '@/features/chat/chatSlice'

/**
 * POST /api/v1/chat mutation'ı (docs/frontend-architecture.md §5, §8).
 * `send` kullanıcı mesajını iyimser olarak thread'e yazar ve isteği atar;
 * `retry` son başarısız isteği kullanıcı balonunu ÇOĞALTMADAN yeniden dener.
 * loading/error React Query'den okunur; session id slice'tan gelir.
 */
export function useSendMessage() {
  const dispatch = useAppDispatch()
  const sessionId = useAppSelector((s) => s.chat.sessionId)

  const mutation = useMutation<SendMessageResponse, ApiError, string>({
    mutationFn: (message) =>
      chatApi.sendMessage({ sessionId: sessionId ?? undefined, message }),
    onSuccess: (response) => {
      dispatch(assistantReplied(response))
    },
  })

  const send = (message: string) => {
    dispatch(userMessageSent(message))
    mutation.mutate(message)
  }

  const retry = () => {
    if (mutation.variables !== undefined) mutation.mutate(mutation.variables)
  }

  return { ...mutation, send, retry }
}
