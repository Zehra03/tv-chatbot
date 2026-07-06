import { useAppSelector } from '@/app/hooks'
import { AnimatedAIChat } from '@/components/ui/animated-ai-chat'
import { MessageList } from '@/features/chat/MessageList'
import { CriteriaChips } from '@/features/chat/CriteriaChips'
import { SessionSidebar } from '@/features/chat/SessionSidebar'
import { useSendMessage } from '@/features/chat/useSendMessage'

/**
 * /chat — Chatbot ana ekranı (docs/frontend-architecture.md §3, §8):
 * geçmiş paneli + mesaj thread'i + biriken kriter rozetleri + composer.
 * AnimatedAIChat HEP monte kalır: boş sohbette hero (başlık + hızlı eylemler,
 * ortalanmış), ilk mesajla hero öğeleri kapanıp aynı cam kart alta iner ve
 * kalıcı composer olur — bileşen sökülmediği için görünüm bozulmaz.
 * Hero kendi arka planını çizmez — Layout'taki NightSkyBackground arkada kalır.
 * Bekleyen açıklayıcı soru composer placeholder'ında ipucu olarak görünür.
 * Chatbot yalnızca arar/listeler/yönlendirir; booking'i kontrollü form yapar.
 * Yükseklik: header 6rem (Layout h-24) + main dikey padding 4rem = 10rem.
 */
export function ChatPage() {
  const sendMessage = useSendMessage()
  const pendingQuestion = useAppSelector((s) => s.chat.pendingQuestion)
  const isEmpty = useAppSelector((s) => s.chat.messages.length === 0)

  return (
    <div className="mx-auto flex h-[calc(100vh-10rem)] max-w-5xl gap-4 supports-[height:100dvh]:h-[calc(100dvh-10rem)]">
      <SessionSidebar />
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        {!isEmpty && (
          <>
            <MessageList
              pending={sendMessage.isPending}
              error={sendMessage.error}
              onRetry={sendMessage.retry}
            />
            <CriteriaChips />
          </>
        )}
        <AnimatedAIChat
          hero={isEmpty}
          onSend={sendMessage.send}
          disabled={sendMessage.isPending}
          placeholder={pendingQuestion}
        />
      </div>
    </div>
  )
}
