import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * Tutarlı hata durumu — mesaj role="alert" içinde, eylem (Tekrar dene)
 * DIŞINDA kardeş olarak durur ki alert atomik okunurken buton kaybolmasın.
 * `retrying` sırasında buton kilitlenir (tekrar denemenin geri bildirimi).
 *
 * Yazı/ikon `destructive-emphasis` kullanır, `destructive` DEĞİL: ikincisi bir zemin
 * rengidir ve yazı olarak koyu temada lacivert üstünde 1.55:1'e düşüyordu — uygulamadaki
 * HER hata mesajı (chat, otel, uçuş, rezervasyon, profil buradan geçer) okunmaz haldeydi.
 */
interface ErrorStateProps {
  message: string
  onRetry?: () => void
  retrying?: boolean
}

export function ErrorState({ message, onRetry, retrying = false }: ErrorStateProps) {
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm">
      <AlertTriangle className="h-4 w-4 shrink-0 text-destructive-emphasis" aria-hidden />
      <p role="alert" className="text-destructive-emphasis">
        {message}
      </p>
      {onRetry && (
        <Button type="button" variant="outline" size="sm" onClick={onRetry} disabled={retrying}>
          {retrying ? 'Deneniyor…' : 'Tekrar dene'}
        </Button>
      )}
    </div>
  )
}
