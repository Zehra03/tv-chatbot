import { useAppSelector } from '@/app/hooks'
import { MessageList } from '@/features/chat/MessageList'
import { Composer } from '@/features/chat/Composer'
import { CriteriaChips } from '@/features/chat/CriteriaChips'
import { SessionSidebar } from '@/features/chat/SessionSidebar'
import { useSendMessage } from '@/features/chat/useSendMessage'

/**
 * /chat — Chatbot ana ekranı (docs/frontend-architecture.md §3, §8):
 * geçmiş paneli + mesaj thread'i + biriken kriter rozetleri + composer.
 * Bekleyen açıklayıcı soru composer placeholder'ında ipucu olarak görünür.
 * Chatbot yalnızca arar/listeler/yönlendirir; booking'i kontrollü form yapar.
 * Yükseklik: header 5rem + main dikey padding 4rem = 9rem.
 */
export function ChatPage() {
  const sendMessage = useSendMessage()
  const pendingQuestion = useAppSelector((s) => s.chat.pendingQuestion)

  return (
    <div className="mx-auto flex h-[calc(100vh-9rem)] max-w-5xl gap-4 supports-[height:100dvh]:h-[calc(100dvh-9rem)]">
      <SessionSidebar />
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <MessageList
          pending={sendMessage.isPending}
          error={sendMessage.error}
          onRetry={sendMessage.retry}
        />
        <CriteriaChips />
        <Composer
          onSend={sendMessage.send}
          disabled={sendMessage.isPending}
          placeholder={pendingQuestion}
        />
      </div>
    </div>
  )
}
