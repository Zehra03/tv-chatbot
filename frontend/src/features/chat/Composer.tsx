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
      <Input
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder ?? 'Mesajınızı yazın…'}
        disabled={disabled}
        aria-label="Mesaj"
        autoComplete="off"
      />
      <Button type="submit" disabled={disabled || !text.trim()}>
        <Send className="h-4 w-4" />
        Gönder
      </Button>
    </form>
  )
}
