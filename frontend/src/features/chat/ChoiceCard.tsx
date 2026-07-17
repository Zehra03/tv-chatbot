import type { ChoiceOption } from '@/types'
import { cn } from '@/lib/utils'

/**
 * Belirsizlik ("hangisini demek istediniz?") kartı — asistan otel mi uçuş mu istendiğini
 * çözemediğinde thread'de balonun altında tıklanabilir seçenekler gösterir. Tıklama, seçeneğin
 * `value`'sunu yeni bir kullanıcı mesajı olarak gönderir (onSelect → useSendMessage.send), yani
 * normal sohbet akışı işler — booking yapılmaz. İstek uçuştayken (`disabled`) butonlar kilitlidir.
 */
export function ChoiceCard({
  options,
  onSelect,
  disabled,
}: {
  options: ChoiceOption[]
  onSelect: (value: string) => void
  disabled?: boolean
}) {
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Seçenekler">
      {options.map((opt) => (
        <button
          key={opt.label}
          type="button"
          disabled={disabled}
          onClick={() => onSelect(opt.value)}
          className={cn(
            'rounded-full border border-foreground/15 bg-foreground/5 px-4 py-2 text-sm font-medium text-foreground',
            'backdrop-blur transition hover:border-foreground/25 hover:bg-foreground/10',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue/60',
            'disabled:cursor-not-allowed disabled:opacity-50',
          )}
        >
          {opt.label}
        </button>
      ))}
    </div>
  )
}
