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

export function Composer({ onSend, disabled, placeholder }: ComposerProps) {
  const [text, setText] = useState('')

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || disabled) return
    onSend(trimmed)
    setText('')
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      {/* glow-group: odakta input altındaki teal çizgi 0→%100 genişler (index.css). */}
      <div className="glow-group relative flex-1">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={placeholder ?? 'Mesajınızı yazın…'}
          disabled={disabled}
          aria-label="Mesaj"
          autoComplete="off"
          className="h-11 rounded-xl border-white/15 bg-white/5 text-white placeholder:text-white/40 focus-visible:ring-brand-teal"
        />
        <div className="input-glow-bar" aria-hidden="true"></div>
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
