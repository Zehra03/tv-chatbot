import { useQuery } from '@tanstack/react-query'
import {
  adminApi,
  type AdminReservationQuery,
  type AdminUser,
  type ApiError,
  type DashboardStats,
} from '@/api'
import type { Page, ReservationSummary } from '@/types'
import { useAppSelector } from '@/app/hooks'
import { selectIdentity } from '@/features/auth/selectors'

/**
 * Admin query key ön-eki — tek bir invalidate çağrısı (`['admin']`) panelin bütün
 * listelerini ve sayaçlarını birlikte tazeler. Bir rezervasyon iptal edildiğinde hem liste
 * hem dashboard sayacı eskir; ikisini ayrı ayrı hatırlamak zorunda kalmayalım.
 */
export const ADMIN_KEY = ['admin'] as const

/**
 * Query key'ler aktif kimliği taşır (useReservations ile aynı örüntü): bir yöneticinin
 * cache'lenmiş verisi, çıkış yapıp başka hesapla devam edene ASLA gösterilmez.
 */
export function useAdminStats() {
  const identity = useAppSelector(selectIdentity)
  return useQuery<DashboardStats, ApiError>({
    queryKey: [...ADMIN_KEY, 'stats', identity ?? 'anon'],
    queryFn: () => adminApi.getStats(),
  })
}

export function useAdminUsers(page: number, size = 20) {
  const identity = useAppSelector(selectIdentity)
  return useQuery<Page<AdminUser>, ApiError>({
    queryKey: [...ADMIN_KEY, 'users', identity ?? 'anon', page, size],
    queryFn: () => adminApi.listUsers(page, size),
  })
}

export function useAdminReservations(query: AdminReservationQuery) {
  const identity = useAppSelector(selectIdentity)
  return useQuery<Page<ReservationSummary>, ApiError>({
    // Filtreler key'in parçası: her filtre kombinasyonu ayrı bir cache girdisi, böylece
    // aramada geri gidince sonuç anında gelir ve iki filtre birbirinin verisini göstermez.
    queryKey: [...ADMIN_KEY, 'reservations', identity ?? 'anon', query],
    queryFn: () => adminApi.listReservations(query),
    // Sayfa/filtre değişiminde tabloyu boşaltıp "Yükleniyor"a düşmek yerine eski satırları
    // tutar; sayfalama sıçramasız hissedilir.
    placeholderData: (prev) => prev,
  })
}
