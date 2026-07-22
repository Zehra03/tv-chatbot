import { useEffect, useState } from 'react'
import { Search, X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { Input } from '@/components/ui/input'
import { Modal } from '@/components/ui/modal'
import { Spinner } from '@/components/ui/spinner'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { useDebouncedValue } from '@/lib/useDebouncedValue'
import {
  RESERVATION_PRODUCT_TYPE_LABELS,
  RESERVATION_STATUS_LABELS,
  reservationStatusVariant,
} from '@/features/reservation/status'
import type { AdminReservationRow } from '@/api'
import type { ReservationStatus } from '@/types'
import { formatDate, formatPrice } from '@/utils/format'
import { AdminCell, AdminPagination, AdminRow, AdminTable } from './AdminPage'
import { useAdminReservations } from './useAdminData'
import { useAdminCancelReservation } from './useAdminCancelReservation'

/**
 * Rezervasyon listesi + filtreler + iptal — hem /admin/reservations hem /admin/flights bunu
 * kullanır; ikincisi `productType` vererek listeyi uçuşlara sabitler. Tek bir tablo/iptal
 * mantığı iki ekranda paylaşılır, aynı davranış iki yerde bakım gerektirmez.
 */

const STATUS_OPTIONS = [
  { value: '', label: 'Tüm durumlar' },
  { value: 'pending', label: RESERVATION_STATUS_LABELS.pending },
  { value: 'confirmed', label: RESERVATION_STATUS_LABELS.confirmed },
  { value: 'cancelled', label: RESERVATION_STATUS_LABELS.cancelled },
  { value: 'failed', label: RESERVATION_STATUS_LABELS.failed },
]

/** Yalnızca bu iki durumdaki rezervasyon iptal edilebilir (kullanıcı tarafıyla aynı kural). */
function cancellable(status: ReservationStatus) {
  return status === 'confirmed' || status === 'pending'
}

/**
 * İptal onayı. Sebep zorunlu (backend `@NotBlank`) ve backend onu TourVisio'ya iletir.
 *
 * Kullanıcı tarafındaki detay ekranı sebebi canlı `cancellationOptions` listesinden seçtirir;
 * admin listesinde o seçenekler yok (detay endpoint'i sahibine kilitli), bu yüzden burada serbest
 * metin alınır. TourVisio sebebi kabul etmezse backend 422 döner ve mesajı olduğu gibi gösteririz —
 * iptal olmuşmuş gibi davranmayız.
 */
function CancelDialog({
  reservation,
  onClose,
}: {
  reservation: AdminReservationRow
  onClose: () => void
}) {
  const cancel = useAdminCancelReservation(reservation.id)
  const [reason, setReason] = useState('')

  // Başarıyla (ya da belirsiz sonuçla) dönünce kapan — toast'ı hook basıyor.
  useEffect(() => {
    if (cancel.isSuccess) onClose()
  }, [cancel.isSuccess, onClose])

  return (
    <Modal open onClose={onClose} title="Rezervasyonu iptal et">
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          <span className="font-mono font-semibold text-foreground">
            {reservation.reservationNumber}
          </span>{' '}
          numaralı rezervasyon iptal edilecek. Bu işlem TourVisio'ya iletilir ve geri alınamaz.
        </p>

        <div className="grid gap-1.5">
          <label htmlFor="admin-cancel-reason" className="text-sm text-muted-foreground">
            İptal sebebi
          </label>
          <Input
            id="admin-cancel-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Örn. müşteri talebi"
            autoFocus
          />
        </div>

        {cancel.isError && (
          <p role="alert" className="text-sm text-destructive-emphasis">
            {apiErrorMessage(cancel.error)}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={cancel.isPending}>
            Vazgeç
          </Button>
          <Button
            variant="destructive"
            disabled={!reason.trim() || cancel.isPending}
            onClick={() => cancel.mutate({ reason: reason.trim() })}
          >
            {cancel.isPending ? (
              <>
                <Spinner size={16} decorative className="text-foreground" />
                İptal ediliyor…
              </>
            ) : (
              'Rezervasyonu iptal et'
            )}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

export function AdminReservationList({
  productType,
  emptyMessage,
}: {
  /** Verilirse liste bu ürün tipine sabitlenir (uçuş ekranı) ve filtre gösterilmez. */
  productType?: string
  emptyMessage: string
}) {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [cancelTarget, setCancelTarget] = useState<AdminReservationRow | null>(null)

  // Her tuşta backend'e gitmemek için gecikmeli; sorgu bu değerle kurulur.
  const debouncedSearch = useDebouncedValue(search, 350)

  // Filtre değişince ilk sayfaya dön: 5. sayfadayken arama yapmak, sonucu 5 sayfa olmayan bir
  // listenin boş 5. sayfasında gösterirdi ("kayıt yok" gibi görünür).
  useEffect(() => {
    setPage(0)
  }, [debouncedSearch, status, productType])

  const { data, isError, isFetching, error, refetch } = useAdminReservations({
    page,
    q: debouncedSearch,
    status: (status || undefined) as ReservationStatus | undefined,
    productType,
  })

  const rows = data?.content ?? []
  const filtering = debouncedSearch.trim() !== '' || status !== ''

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="PNR ile ara (kendi kodumuz veya TourVisio numarası)"
            aria-label="PNR ile ara"
            className="pl-9 pr-9"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
              aria-label="Aramayı temizle"
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        <div className="sm:w-48">
          <DropdownSelect
            value={status}
            options={STATUS_OPTIONS}
            onChange={setStatus}
            aria-label="Duruma göre filtrele"
          />
        </div>
      </div>

      {isFetching && !data && <LoadingState label="Rezervasyonlar yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={apiErrorMessage(error)} onRetry={() => refetch()} />
      )}

      {data && rows.length === 0 && (
        <EmptyState>{filtering ? 'Bu filtreye uyan kayıt yok.' : emptyMessage}</EmptyState>
      )}

      {rows.length > 0 && (
        <AdminTable
          headers={['PNR', 'Tip', 'Tarih', 'Hesap', 'Yolcu', 'Tutar', 'Durum', '']}
        >
          {rows.map((r) => (
            <AdminRow key={r.id}>
              {/* PNR sarmamalı: tek parça bir koddur, üç satıra bölününce taranamaz hâle gelir.
                  Hesap sütunu genişleyince satır sonu buraya düşüyordu. */}
              <AdminCell label="PNR" className="md:whitespace-nowrap">
                <span className="font-mono text-sm font-semibold text-foreground">
                  {r.reservationNumber}
                </span>
                {r.externalReservationNumber && (
                  <span className="block text-xs text-muted-foreground">
                    {r.externalReservationNumber}
                  </span>
                )}
              </AdminCell>
              <AdminCell label="Tip">
                {RESERVATION_PRODUCT_TYPE_LABELS[r.productType]}
              </AdminCell>
              <AdminCell label="Tarih" className="whitespace-nowrap">
                {formatDate(r.reservationDate)}
              </AdminCell>
              {/* Hesap = rezervasyonu yapan KAYITLI KULLANICI; Yolcu = bilette yazan lider ad.
                  İkisi ayrı sütun çünkü aynı şey değiller: bir üye başkası adına rezervasyon
                  yapabilir. Misafir rezervasyonun hesabı yoktur — rozet bunu söyler. */}
              {/* E-postalar uzun olabiliyor; sınırlanmazsa tablo yatayda taşıp eylem sütununu
                  kaydırma alanının dışına itiyor. Kırpılan adresin tamamı title ile erişilebilir. */}
              <AdminCell label="Hesap" className="md:max-w-[13rem]">
                <span className="flex flex-wrap items-center justify-end gap-2 md:justify-start">
                  {r.guest ? (
                    <Badge variant="warning">Misafir</Badge>
                  ) : (
                    <span className="block min-w-0 max-w-[13rem]">
                      <span className="block truncate" title={r.ownerEmail ?? undefined}>
                        {r.ownerEmail ?? '—'}
                      </span>
                      {r.ownerName && (
                        <span className="block truncate text-xs text-muted-foreground">
                          {r.ownerName}
                        </span>
                      )}
                    </span>
                  )}
                </span>
              </AdminCell>
              <AdminCell label="Yolcu">
                <span className="truncate">{r.leadGuestName ?? '—'}</span>
              </AdminCell>
              <AdminCell label="Tutar" className="whitespace-nowrap font-semibold">
                {formatPrice(r.totalAmount, r.currency)}
              </AdminCell>
              <AdminCell label="Durum">
                <Badge variant={reservationStatusVariant(r.status)}>
                  {RESERVATION_STATUS_LABELS[r.status]}
                </Badge>
              </AdminCell>
              <AdminCell label="İşlem" className="md:text-right">
                {cancellable(r.status) ? (
                  <Button variant="outline" size="sm" onClick={() => setCancelTarget(r)}>
                    İptal et
                  </Button>
                ) : (
                  <span className="text-xs text-muted-foreground">—</span>
                )}
              </AdminCell>
            </AdminRow>
          ))}
        </AdminTable>
      )}

      {data && (
        <AdminPagination
          page={data.number}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onPageChange={setPage}
          busy={isFetching}
        />
      )}

      {cancelTarget && (
        <CancelDialog reservation={cancelTarget} onClose={() => setCancelTarget(null)} />
      )}
    </div>
  )
}
