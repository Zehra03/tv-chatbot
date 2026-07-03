import { Spinner } from '@/components/ui/spinner'

/**
 * Tutarlı yüklenme durumu — tüm sayfalar aynı görünümü kullanır.
 * role="status" sarmalayıcıdadır; spinner dekoratiftir ki ekran okuyucu
 * yalnızca görünür etiketi duyursun.
 */
export function LoadingState({ label }: { label: string }) {
  return (
    <div role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
      <Spinner size={16} decorative />
      {label}
    </div>
  )
}
