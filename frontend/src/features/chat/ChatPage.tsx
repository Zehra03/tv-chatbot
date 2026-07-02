import { MessageList } from '@/features/chat/MessageList'
import { Composer } from '@/features/chat/Composer'
import { useSendMessage } from '@/features/chat/useSendMessage'

/**
 * /chat — Chatbot ana ekranı (docs/frontend-architecture.md §3, §8):
 * mesaj thread'i + composer. Chatbot yalnızca arar/listeler/yönlendirir;
 * booking'i kontrollü rezervasyon formu yapar.
 */
export function ChatPage() {
  const sendMessage = useSendMessage()

  return (
    <div className="mx-auto flex h-[calc(100vh-8rem)] max-w-3xl flex-col gap-4">
      <MessageList pending={sendMessage.isPending} error={sendMessage.error} />
      <Composer onSend={(text) => sendMessage.mutate(text)} disabled={sendMessage.isPending} />
    </div>
  )
}
