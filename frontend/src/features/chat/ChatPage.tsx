import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { AnimatedAIChat } from '@/components/ui/animated-ai-chat'
import { MessageList } from '@/features/chat/MessageList'
import { CriteriaChips } from '@/features/chat/CriteriaChips'
import { SessionSidebar } from '@/features/chat/SessionSidebar'
import { ResultsDrawer, ResultsPanel } from '@/features/chat/ResultsPanel'
import { useSendMessage } from '@/features/chat/useSendMessage'
import { resolveCommand } from '@/features/chat/commands'
import { isSearchTurn } from '@/features/chat/isSearchTurn'
import { useDelayedFlag } from '@/lib/useDelayedFlag'

/**
 * "Arıyorum…" yalnız gerçek bir arama BU KADAR (ms) sürünce açılır. İki koşul birlikte:
 * (1) tur bir arama olmalı (isSearchTurn — kriterler tam ya da son eksik soruluyor);
 * (2) istek bu eşiği aşacak kadar sürmeli. (2) hızlı cache-hit aramalarda göstergeyi
 * gereksiz çaktırmaz; (1) normal sohbet/slot-filling'de hiç açılmamasını sağlar.
 */
const SEARCH_HINT_DELAY_MS = 700

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
  const navigate = useNavigate()
  const pendingQuestion = useAppSelector((s) => s.chat.pendingQuestion)
  const messages = useAppSelector((s) => s.chat.messages)
  // Biriken arama kriteri — sonuç panelindeki "Seç"in rezervasyon snapshot'ını kurabilmesi için
  // kartlara iletilir (otelde giriş/çıkış-oda/kişi, uçuşta yolcu sayısı ürün dışında burada yaşar).
  const criteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  const isEmpty = messages.length === 0

  // "Arıyorum…" göstergesi: yalnız uçuştaki istek gerçek bir arama (isSearchTurn) VE
  // eşiği aşacak kadar uzunsa açılır — normal sohbet/slot-filling'de veya hızlı
  // yanıtta gösterge sade kalır (PPMO K24). Aynı sinyal açık sonuç panelinde
  // "Aranıyor…" spinner'ını sürer.
  const slowEnough = useDelayedFlag(sendMessage.isPending, SEARCH_HINT_DELAY_MS)
  const searching = isSearchTurn(criteria, pendingQuestion) && slowEnough

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

  // Composer'dan gelen ham metni backend'e GİTMEDEN önce komut olarak çözer:
  // "/rezervasyon" → sohbete mesaj yazmadan /reservations'a yönlendir; arama
  // komutları doğal cümleye çevrilip normal akışa girer (kullanıcı balonunda "/otel"
  // değil "Otel aramak istiyorum" görünür, intent doğru tetiklenir); komut olmayan
  // mesaj olduğu gibi gider. Boş metin (ör. yalnız "/") gönderilmez.
  const handleSend = (raw: string) => {
    const resolved = resolveCommand(raw)
    if (resolved?.type === 'navigate') {
      navigate(resolved.to)
      return
    }
    const text = resolved ? resolved.text : raw
    if (text.trim()) sendMessage.send(text)
  }

  return (
    <div className="flex min-h-0 w-full flex-1 gap-4">
      <SessionSidebar />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-4">
        {!isEmpty && (
          <>
            <MessageList
              pending={sendMessage.isPending}
              searching={searching}
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
          onSend={handleSend}
          disabled={sendMessage.isPending}
          placeholder={pendingQuestion}
        />
      </div>

      {/* Masaüstü sonuç ekranı — sohbetin sağında sabit; sonuç varken belirir. */}
      {hasResults && (
        <ResultsPanel
          cards={activeCards}
          criteria={criteria}
          loading={searching}
          className="hidden w-[360px] shrink-0 lg:flex xl:w-[400px]"
        />
      )}

      {/* Mobil sonuç ekranı — sağdan kayan kapanabilir katman (lg+'da gizli). */}
      <ResultsDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        cards={activeCards}
        criteria={criteria}
        loading={searching}
      />
    </div>
  )
}
