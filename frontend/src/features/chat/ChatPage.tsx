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
 * Yükseklik: Layout kabuğu viewport'a kilitli; kap flex-1 ile main'in kalan
 * alanını tam doldurur (sabit 100vh-hesabı yok — header değişse de bozulmaz).
 * Kendi iç alanları (geçmiş paneli, mesaj thread'i) bağımsız kayar.
 */
export function ChatPage() {
  const sendMessage = useSendMessage()
  const pendingQuestion = useAppSelector((s) => s.chat.pendingQuestion)
  const isEmpty = useAppSelector((s) => s.chat.messages.length === 0)

  return (
    <div className="mx-auto flex min-h-0 w-full max-w-6xl flex-1 gap-4">
      <SessionSidebar />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-4">
        {!isEmpty && (
          <>
            <MessageList
              pending={sendMessage.isPending}
              error={sendMessage.error}
              onRetry={sendMessage.retry}
              onSelectOption={sendMessage.send}
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
