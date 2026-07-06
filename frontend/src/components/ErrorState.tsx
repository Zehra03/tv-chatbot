import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * Tutarlı hata durumu — mesaj role="alert" içinde, eylem (Tekrar dene)
 * DIŞINDA kardeş olarak durur ki alert atomik okunurken buton kaybolmasın.
 * `retrying` sırasında buton kilitlenir (tekrar denemenin geri bildirimi).
 * Yumuşak destructive zemin her iki bölgede de (açık/koyu) okunur kalır.
 */
interface ErrorStateProps {
  message: string
  onRetry?: () => void
  retrying?: boolean
}

export function ErrorState({ message, onRetry, retrying = false }: ErrorStateProps) {
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm">
      <AlertTriangle className="h-4 w-4 shrink-0 text-destructive" aria-hidden />
      <p role="alert" className="text-destructive">
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
