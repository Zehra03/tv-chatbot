import { useEffect, useMemo, useState } from 'react'
import { useAppSelector } from '@/app/hooks'
import { AnimatedAIChat } from '@/components/ui/animated-ai-chat'
import { MessageList } from '@/features/chat/MessageList'
import { CriteriaChips } from '@/features/chat/CriteriaChips'
import { SessionSidebar } from '@/features/chat/SessionSidebar'
import { ResultsDrawer, ResultsPanel } from '@/features/chat/ResultsPanel'
import { useSendMessage } from '@/features/chat/useSendMessage'

/**
 * /chat — Chatbot ana ekranı (docs/frontend-architecture.md §3, §8):
 * geçmiş paneli + mesaj thread'i + biriken kriter rozetleri + composer, ve
 * sonuç varsa SAĞDA sonuç ekranı (madde 8). Sonuç kartları artık sohbet
 * balonlarına yığılmaz; sohbet sola daralır, kartlar sağ panelde (mobilde
 * kapanabilir drawer'da) kompakt listelenir. Thread'deki "Sonuçları göster"
 * düğmesi ilgili kümeyi panele/drawer'a taşır.
 *
 * AnimatedAIChat HEP monte kalır: boş sohbette hero, ilk mesajla alta iner.
 * Chatbot yalnızca arar/listeler/yönlendirir; booking'i kontrollü form yapar.
 */
export function ChatPage() {
  const sendMessage = useSendMessage()
  const pendingQuestion = useAppSelector((s) => s.chat.pendingQuestion)
  const messages = useAppSelector((s) => s.chat.messages)
  // Biriken arama kriteri — sonuç panelindeki "Seç"in rezervasyon snapshot'ını kurabilmesi için
  // kartlara iletilir (otelde giriş/çıkış-oda/kişi, uçuşta yolcu sayısı ürün dışında burada yaşar).
  const criteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  const isEmpty = messages.length === 0

  // Kart taşıyan mesajlar = sonuç kümeleri. Panel varsayılan olarak EN SON
  // kümeyi gösterir; kullanıcı eski bir "Sonuçları göster"e basarsa o küme öne
  // gelir. Yeni arama gelince (latestId değişince) panel yeniden en sona döner.
  const resultMessages = useMemo(
    () => messages.filter((m) => m.cards && m.cards.length > 0),
    [messages],
  )
  const latestId = resultMessages.length ? resultMessages[resultMessages.length - 1].id : null
  const [activeId, setActiveId] = useState<string | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    setActiveId(latestId)
  }, [latestId])

  const activeMessage =
    resultMessages.find((m) => m.id === activeId) ??
    resultMessages[resultMessages.length - 1]
  const activeCards = activeMessage?.cards ?? []
  const hasResults = activeCards.length > 0

  const handleShowResults = (messageId: string) => {
    setActiveId(messageId)
    setDrawerOpen(true) // Mobilde drawer açılır; lg+'da docked panel zaten görünür.
  }

  return (
    <div className="flex min-h-0 w-full flex-1 gap-4">
      <SessionSidebar />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-4">
        {!isEmpty && (
          <>
            <MessageList
              pending={sendMessage.isPending}
              error={sendMessage.error}
              onRetry={sendMessage.retry}
              onSelectOption={sendMessage.send}
              onShowResults={handleShowResults}
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

      {/* Masaüstü sonuç ekranı — sohbetin sağında sabit; sonuç varken belirir. */}
      {hasResults && (
        <ResultsPanel
          cards={activeCards}
          criteria={criteria}
          className="hidden w-[360px] shrink-0 lg:flex xl:w-[400px]"
        />
      )}

      {/* Mobil sonuç ekranı — sağdan kayan kapanabilir katman (lg+'da gizli). */}
      <ResultsDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        cards={activeCards}
        criteria={criteria}
      />
    </div>
  )
}
