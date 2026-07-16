import { useMutation } from '@tanstack/react-query'
import {
  reservationApi,
  type ApiError,
  type PreviewReservationCommand,
  type PreviewResponse,
} from '@/api'

/**
 * POST /api/v1/reservations/preview — onay öncesi snapshot'ı dondurur ve `previewId` döndürür
 * (docs/frontend-architecture.md §9). Yazma/TourVisio YOK. Toplam tutar istemcide hesaplanmaz —
 * kullanıcının seçtiği üründen gelen `totalAmount` snapshot'la birlikte gönderilir.
 */
export function useReservationPreview() {
  return useMutation<PreviewResponse, ApiError, PreviewReservationCommand>({
    mutationFn: (command) => reservationApi.preview(command),
  })
}
