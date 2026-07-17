import { useState, type FormEvent } from 'react'
import { Send } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

/**
 * Mesaj yazma alanı — Enter veya Gönder ile gönderir, gönderim sonrası temizlenir.
 * `disabled` (istek sürerken) yazmayı ve göndermeyi kilitler. `placeholder`
 * slot-filling'deki bekleyen soruyu ipucu olarak taşıyabilir.
 */
interface ComposerProps {
  onSend: (text: string) => void
  disabled?: boolean
  placeholder?: string
}

/** Tek mesajın azami uzunluğu. */
const MAX_LENGTH = 2000
/** Sayaç bu uzunluktan sonra görünür — sınır yaklaşmadan gürültü yapmasın. */
const COUNTER_VISIBLE_AT = 1800

export function Composer({ onSend, disabled, placeholder }: ComposerProps) {
  const [text, setText] = useState('')
  const showCounter = text.length >= COUNTER_VISIBLE_AT

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || disabled) return
    onSend(trimmed)
    setText('')
  }

  return (
    // items-start: sayaç belirince input sütunu uzar, Gönder input hizasında kalsın.
    <form onSubmit={handleSubmit} className="flex items-start gap-2">
      <div className="flex-1">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={placeholder ?? 'Mesajınızı yazın…'}
          disabled={disabled}
          maxLength={MAX_LENGTH}
          aria-label="Mesaj"
          autoComplete="off"
          className="h-11 rounded-xl border-input bg-background text-foreground placeholder:text-muted-foreground"
        />
        {showCounter && (
          // aria-live=polite: sınıra yaklaşan görme engelli kullanıcı da duysun;
          // her tuşta değil, okuyucunun uygun bulduğu anda seslendirilir.
          <p
            aria-live="polite"
            className={`mt-1 text-end text-xs tabular-nums ${
              text.length >= MAX_LENGTH ? 'text-destructive-emphasis' : 'text-muted-foreground'
            }`}
          >
            {text.length}/{MAX_LENGTH}
          </p>
        )}
      </div>
      <Button
        type="submit"
        disabled={disabled || !text.trim()}
        className="h-11 rounded-xl"
      >
        <Send className="h-4 w-4" />
        Gönder
      </Button>
    </form>
  )
}
