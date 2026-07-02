import { useEffect, useRef } from 'react'
import { Spinner } from '@/components/ui/spinner'
import { ResultCards } from '@/features/chat/ResultCards'
import { useAppSelector } from '@/app/hooks'
import type { ApiError } from '@/api'
import type { ChatMessage } from '@/types'
import { cn } from '@/lib/utils'

/**
 * Sohbet thread'i — mesajları chatSlice'tan okur, kullanıcıyı sağda (primary),
 * asistanı solda (muted) balonlarla gösterir. `pending` iken "yazıyor" göstergesi,
 * `error` durumunda hata satırı render edilir. Yeni mesajda en alta kayar.
 */
interface MessageListProps {
  pending: boolean
  error: ApiError | null
}

function Bubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user'
  return (
    <div className={cn('flex', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[80%] whitespace-pre-wrap rounded-lg px-4 py-2 text-sm',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted text-foreground',
        )}
      >
        {message.content}
      </div>
    </div>
  )
}

export function MessageList({ pending, error }: MessageListProps) {
  const messages = useAppSelector((s) => s.chat.messages)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    // jsdom'da scrollIntoView yok — testte sessizce atlanır.
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' })
  }, [messages.length, pending])

  return (
    <div role="log" aria-label="Sohbet mesajları" className="flex-1 space-y-3 overflow-y-auto pr-1">
      {messages.length === 0 && (
        <div className="flex h-full flex-col items-center justify-center gap-1 text-center">
          <p className="font-semibold">Merhaba! Size nasıl yardımcı olabilirim?</p>
          <p className="max-w-sm text-sm text-muted-foreground">
            Otel veya uçuş aramak için yazın — örn. &quot;Antalya&apos;da 2026-08-01 /
            2026-08-05 arası 2 kişilik otel&quot;.
          </p>
        </div>
      )}
      {messages.map((m) => (
        <div key={m.id} className="space-y-2">
          <Bubble message={m} />
          {m.cards && m.cards.length > 0 && <ResultCards cards={m.cards} />}
        </div>
      ))}
      {pending && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Spinner size={16} />
          Asistan yazıyor…
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error.message}
        </p>
      )}
      <div ref={bottomRef} />
    </div>
  )
}
