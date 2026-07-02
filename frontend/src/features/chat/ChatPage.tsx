import { useAppSelector } from '@/app/hooks'
import { MessageList } from '@/features/chat/MessageList'
import { Composer } from '@/features/chat/Composer'
import { CriteriaChips } from '@/features/chat/CriteriaChips'
import { useSendMessage } from '@/features/chat/useSendMessage'

/**
 * /chat — Chatbot ana ekranı (docs/frontend-architecture.md §3, §8):
 * mesaj thread'i + biriken kriter rozetleri + composer. Bekleyen açıklayıcı
 * soru composer placeholder'ında ipucu olarak görünür. Chatbot yalnızca
 * arar/listeler/yönlendirir; booking'i kontrollü rezervasyon formu yapar.
 */
export function ChatPage() {
  const sendMessage = useSendMessage()
  const pendingQuestion = useAppSelector((s) => s.chat.pendingQuestion)

  return (
    <div className="mx-auto flex h-[calc(100vh-8rem)] max-w-3xl flex-col gap-4">
      <MessageList pending={sendMessage.isPending} error={sendMessage.error} />
      <CriteriaChips />
      <Composer
        onSend={(text) => sendMessage.mutate(text)}
        disabled={sendMessage.isPending}
        placeholder={pendingQuestion}
      />
    </div>
  )
}
