import type { ReactNode } from 'react'
import { motion } from 'framer-motion'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * Panel sayfalarının ortak parçaları. Dört admin ekranı da aynı iskeleti kullanır;
 * başlık/gradyan şeridi ve tablo kabuğunu her sayfada yeniden yazmak yerine burada bir kez.
 * Görsel dil ReservationsPage'den devralınır (başlık + marka şeridi + açıklama).
 */

export function AdminPageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
      className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"
    >
      <div>
        <h1 className="text-2xl font-bold text-foreground">{title}</h1>
        <div
          aria-hidden="true"
          className="mt-1.5 h-1 w-16 rounded-full bg-gradient-to-r from-brand-blue to-brand-steel"
        />
        {description && <p className="mt-2 text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="shrink-0">{actions}</div>}
    </motion.div>
  )
}

/**
 * Tablo kabuğu. Geniş ekranda gerçek bir `<table>`, dar ekranda ise satırlar kart olarak
 * yığılır — sütun başlıkları `hidden md:table-header-group` ile gizlenir ve her hücre kendi
 * etiketini `data-label` üzerinden taşır. Böylece tek bir işaretleme iki düzeni de verir;
 * ayrı bir mobil listesi yazmak (ve iki yerde bakım yapmak) gerekmez.
 *
 * Yatay taşma tablo kabında kalır (`overflow-x-auto`): sayfa gövdesi asla yana kaymaz.
 */
export function AdminTable({
  headers,
  children,
  className,
}: {
  headers: string[]
  children: ReactNode
  className?: string
}) {
  return (
    <Card className={cn('overflow-hidden', className)}>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead className="hidden border-b border-border bg-muted/50 md:table-header-group">
            <tr>
              {headers.map((h) => (
                <th
                  key={h}
                  scope="col"
                  className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">{children}</tbody>
        </table>
      </div>
    </Card>
  )
}

/**
 * Tablo satırı. Mobilde `flex flex-col` ile kart olur; hücreler `AdminCell`'in `label`'ını
 * `::before` yerine görünür bir span olarak taşır (ekran okuyucu da görür).
 */
export function AdminRow({ children }: { children: ReactNode }) {
  return (
    <tr className="flex flex-col gap-1 p-4 transition-colors hover:bg-muted/40 md:table-row md:gap-0 md:p-0">
      {children}
    </tr>
  )
}

export function AdminCell({
  label,
  children,
  className,
}: {
  /** Dar ekranda hücrenin başına yazılan sütun adı — geniş ekranda `<thead>` zaten söylüyor. */
  label: string
  children: ReactNode
  className?: string
}) {
  return (
    <td
      className={cn(
        'flex items-center justify-between gap-3 py-0.5 md:table-cell md:px-4 md:py-3',
        className,
      )}
    >
      <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground md:hidden">
        {label}
      </span>
      <span className="min-w-0 text-right md:text-left">{children}</span>
    </td>
  )
}

/**
 * Sayfalama çubuğu. `page` 0 TABANLIDIR (Spring `Page.number`); ekranda 1 tabanlı gösterilir.
 * Tek sayfa varsa hiç render edilmez — anlamsız bir "1 / 1" satırı bırakmaz.
 */
export function AdminPagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
  busy = false,
}: {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
  busy?: boolean
}) {
  if (totalPages <= 1) return null
  return (
    <div className="flex items-center justify-between gap-3">
      <p className="text-sm text-muted-foreground">
        Sayfa {page + 1} / {totalPages} · {totalElements} kayıt
      </p>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={page <= 0 || busy}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft className="mr-1 h-4 w-4" aria-hidden />
          Önceki
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={page >= totalPages - 1 || busy}
          onClick={() => onPageChange(page + 1)}
        >
          Sonraki
          <ChevronRight className="ml-1 h-4 w-4" aria-hidden />
        </Button>
      </div>
    </div>
  )
}
