import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { adminApi, type ApiError, type CancelRequest, type CancelResult } from '@/api'
import { ADMIN_KEY } from './useAdminData'
import { RESERVATIONS_KEY } from '@/features/reservation/useReservations'

/**
 * PUT /api/v1/admin/reservations/{id}/status — yönetici iptali. `useCancelReservation` ile aynı
 * sözleşme, tek farkı sahiplik kontrolünün atlanması: yönetici kendisine ait olmayan (ve misafir)
 * rezervasyonları da iptal edebilir.
 *
 * 202 (`pending`) BAŞARI DEĞİLDİR: backend TourVisio'dan yanıt alamadığını, iptalin geçmiş de
 * olabileceğini geçmemiş de olabileceğini söylüyor. O dalda yeşil onay yerine backend'in kendi
 * uyarı metni gösterilir — yönetici iptal olduğunu sanıp takibi bırakmasın.
 *
 * Hem admin hem kullanıcı listesi invalidate edilir: aynı rezervasyon iki ekranda da görünür ve
 * biri iptal edilince diğerinin cache'i eskimiş kalırdı.
 */
export function useAdminCancelReservation(id: number) {
  const queryClient = useQueryClient()
  return useMutation<CancelResult, ApiError, CancelRequest>({
    mutationFn: (body) => adminApi.cancelReservation(id, body),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ADMIN_KEY })
      queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
      if (result.kind === 'cancelled') {
        toast.success('Rezervasyon iptal edildi.')
      } else {
        toast.warning(result.outcome.message, { duration: 10000 })
      }
    },
    onError: (error) => {
      toast.error(`İptal edilemedi: ${apiErrorMessage(error)}`)
    },
  })
}
