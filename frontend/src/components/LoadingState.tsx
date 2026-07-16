import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'

/**
 * Tutarlı yüklenme durumu — tüm sayfalar aynı görünümü kullanır.
 * role="status" sarmalayıcıdadır; spinner dekoratiftir ki ekran okuyucu
 * yalnızca görünür etiketi duyursun. Koyu yüzeyde className ile renk geçilir.
 */
export function LoadingState({ label, className }: { label: string; className?: string }) {
  return (
    <div
      role="status"
      className={cn('flex items-center gap-2 text-sm text-muted-foreground', className)}
    >
      <Spinner size={16} decorative />
      {label}
    </div>
  )
}
