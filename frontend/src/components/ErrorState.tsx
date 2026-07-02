import { Button } from '@/components/ui/button'

/**
 * Tutarlı hata durumu — mesaj role="alert" içinde, eylem (Tekrar dene)
 * DIŞINDA kardeş olarak durur ki alert atomik okunurken buton kaybolmasın.
 * `retrying` sırasında buton kilitlenir (tekrar denemenin geri bildirimi).
 */
interface ErrorStateProps {
  message: string
  onRetry?: () => void
  retrying?: boolean
}

export function ErrorState({ message, onRetry, retrying = false }: ErrorStateProps) {
  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
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
