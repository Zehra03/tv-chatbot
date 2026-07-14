import { useEffect, useRef } from 'react'
import { motion, type Variants } from 'framer-motion'
import { ChevronRight, Hotel, Plane } from 'lucide-react'
import { ErrorState } from '@/components/ErrorState'
import { SplitText } from '@/components/SplitText'
import { ChoiceCard } from '@/features/chat/ChoiceCard'
import { TypingIndicator } from '@/features/chat/TypingIndicator'
import { useAppSelector } from '@/app/hooks'
import type { ApiError } from '@/api'
import type { ChatMessage, ResultCard } from '@/types'
import { cn } from '@/lib/utils'

/**
 * Sohbet thread'i — mesajları chatSlice'tan okur; gece uçuşu yüzeyinde
 * kullanıcıyı sağda (mavi→teal gradyan), asistanı solda (cam balon) gösterir.
 * `pending` iken yazıyor göstergesi, `error` durumunda hata satırı + Tekrar
 * dene render edilir. Yeni mesajda en alta kayar; girişler stagger ile belirir.
 */
interface MessageListProps {
  pending: boolean
  error: ApiError | null
  onRetry?: () => void
  /** Belirsizlik kartındaki bir seçenek seçilince çağrılır (value yeni tur olarak gönderilir). */
  onSelectOption?: (value: string) => void
  /** Bir sonuç mesajının "Sonuçları göster" düğmesine basılınca çağrılır —
   * o kümeyi sağ panele/mobil drawer'a taşır (madde 8). */
  onShowResults?: (messageId: string) => void
}

/** Thread içi kompakt sonuç özeti — kartları balonların içinde yığmak yerine
 * (mobilde ekranı kaplıyordu) sonuçları sağ panele/drawer'a açan tek düğme. */
function ResultSummaryButton({
  cards,
  onClick,
}: {
  cards: ResultCard[]
  onClick: () => void
}) {
  const isFlight = cards[0]?.productType === 'flight'
  const Icon = isFlight ? Plane : Hotel
  const noun = isFlight ? 'uçuş' : 'otel'
  return (
    <button
      type="button"
      onClick={onClick}
      className="glass-card group flex w-full items-center gap-3 rounded-xl p-3 text-left transition-all duration-300 hover:border-brand-teal/60 hover:shadow-[0_8px_30px_theme(colors.brand.teal/15%)]"
    >
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-teal/15 text-brand-teal">
        <Icon className="h-4 w-4" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold text-white">
          {cards.length} {noun} sonucu
        </span>
        <span className="block text-xs text-brand-ice/60">Sonuçları göster</span>
      </span>
      <ChevronRight
        className="h-4 w-4 shrink-0 text-brand-ice/50 transition-transform group-hover:translate-x-0.5"
        aria-hidden
      />
    </button>
  )
}

const listVariants: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 10 },
  show: { opacity: 1, y: 0, transition: { duration: 0.25, ease: 'easeOut' } },
}

function Bubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user'
  return (
    <div className={cn('flex', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[80%] whitespace-pre-wrap break-words px-4 py-2.5 text-sm text-white',
          isUser
            ? 'rounded-2xl rounded-br-md bg-gradient-to-br from-brand-blue to-brand-teal shadow-[0_4px_20px_theme(colors.brand.blue/25%)]'
            : 'glass-card rounded-bl-md',
        )}
      >
        {/* Ekran okuyucu için gönderen — görsel ayrım yalnızca hizalama/renkte. */}
        <span className="sr-only">{isUser ? 'Siz: ' : 'Asistan: '}</span>
        {message.content}
      </div>
    </div>
  )
}

export function MessageList({
  pending,
  error,
  onRetry,
  onSelectOption,
  onShowResults,
}: MessageListProps) {
  const messages = useAppSelector((s) => s.chat.messages)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    // jsdom'da scrollIntoView yok — testte sessizce atlanır.
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' })
  }, [messages.length, pending])

  // overflow-x-hidden: kart hover animasyonları yatay scrollbar titretmesin.
  // relative ŞART: mesaj balonlarındaki sr-only etiketleri position:absolute'tur;
  // kap konumlandırılmazsa bunların kapsayan bloğu <main> olur (en yakın
  // konumlandırılmış ata), böylece bu kabın overflow kırpmasından KAÇARLAR ve
  // main.scrollHeight'i tüm konuşma boyuna şişirirler — sonra scrollIntoView
  // main'i kaydırıp başlığı iter, altta boşluk bırakır. relative ile sr-only'ler
  // bu kaba kapsanıp kırpılır; main sabit yükseklikte kalır.
  return (
    <div
      role="log"
      aria-label="Sohbet mesajları"
      className="relative flex-1 space-y-3 overflow-y-auto overflow-x-hidden pr-1 [scrollbar-color:theme(colors.white/25%)_transparent] [scrollbar-width:thin]"
    >
      {messages.length === 0 && (
        <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
          {/* Harf harf giriş (SplitText) — bu metin testlerde sorgulanmıyor.
              text-gradient-brand kullanılamaz: bg-clip:text, GSAP'ın
              inline-block harf span'larıyla Chromium'da hiç boyanmıyor
              (harfler görünmez kalıyor) — düz beyaz + harf animasyonu. */}
          <SplitText
            text="Merhaba! Size nasıl yardımcı olabilirim?"
            tag="p"
            className="text-2xl font-bold tracking-tight text-white"
            splitType="chars"
            delay={30}
            duration={0.9}
          />
          <p className="max-w-sm text-sm text-brand-ice/70">
            Otel veya uçuş aramak için yazın — örn. &quot;Antalya&apos;da 2026-08-01 /
            2026-08-05 arası 2 kişilik otel&quot;.
          </p>
        </div>
      )}
      <motion.div variants={listVariants} initial="hidden" animate="show" className="space-y-3">
        {messages.map((m) => (
          <motion.div key={m.id} variants={itemVariants} className="space-y-2">
            <Bubble message={m} />
            {m.cards && m.cards.length > 0 && (
              <ResultSummaryButton cards={m.cards} onClick={() => onShowResults?.(m.id)} />
            )}
            {m.options && m.options.length > 0 && (
              <ChoiceCard
                options={m.options}
                onSelect={(value) => onSelectOption?.(value)}
                disabled={pending}
              />
            )}
          </motion.div>
        ))}
      </motion.div>
      {pending && <TypingIndicator />}
      {error && !pending && <ErrorState message={error.message} onRetry={onRetry} />}
      <div ref={bottomRef} />
    </div>
  )
}
