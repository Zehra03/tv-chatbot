import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { formatDate } from '@/utils/format'
import { AdminCell, AdminPageHeader, AdminPagination, AdminRow, AdminTable } from './AdminPage'
import { useAdminUsers } from './useAdminData'

/**
 * /admin/users — kayıtlı kullanıcılar ve rolleri.
 *
 * Roller SALT OKUNUR. Rol değiştirme kasıtlı olarak yok: backend'de böyle bir uç yok ve rol
 * yükseltme, arkasında denetim kaydı olması gereken bir işlem — buraya sessizce bir düğme
 * koymaktansa ayrı ele alınmalı. Bugün rol yükseltme DB'den yapılıyor.
 */
export function AdminUsersPage() {
  const [page, setPage] = useState(0)
  const { data, isError, isFetching, error, refetch } = useAdminUsers(page)

  const rows = data?.content ?? []

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="Kullanıcı Yönetimi"
        description={
          data
            ? `Kayıtlı kullanıcılar ve rolleri · ${data.totalElements} kullanıcı.`
            : 'Kayıtlı kullanıcılar ve rolleri.'
        }
      />

      {isFetching && !data && <LoadingState label="Kullanıcılar yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={apiErrorMessage(error)} onRetry={() => refetch()} />
      )}

      {data && rows.length === 0 && <EmptyState>Kayıtlı kullanıcı yok.</EmptyState>}

      {rows.length > 0 && (
        <AdminTable headers={['E-posta', 'Ad', 'Rol', 'Kayıt tarihi']}>
          {rows.map((u) => (
            <AdminRow key={u.id}>
              <AdminCell label="E-posta">
                <span className="truncate font-medium text-foreground">{u.email}</span>
              </AdminCell>
              <AdminCell label="Ad">{u.displayName ?? '—'}</AdminCell>
              <AdminCell label="Rol">
                {/* Yönetici rozeti vurgulu: bir listede kimin yetkili olduğu bir bakışta görünmeli. */}
                <Badge variant={u.role === 'ADMIN' ? 'glassAccent' : 'glass'}>{u.role}</Badge>
              </AdminCell>
              <AdminCell label="Kayıt tarihi" className="whitespace-nowrap">
                {u.createdAt ? formatDate(u.createdAt) : '—'}
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
    </div>
  )
}
