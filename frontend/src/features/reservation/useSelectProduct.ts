import { useNavigate } from 'react-router-dom'
import { useAppDispatch } from '@/app/hooks'
import { setDraft, type ReservationDraft } from '@/features/reservation/reservationDraftSlice'

/**
 * Seçilen ürünü reservationDraft'a yazar ve kontrollü rezervasyon formuna
 * yönlendirir (docs/frontend-architecture.md §9) — chat kartları ve sonuç
 * listeleri aynı tek çıkış noktasını kullanır; booking'i yalnızca form yapar.
 */
export function useSelectProduct() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  return (draft: ReservationDraft) => {
    dispatch(setDraft(draft))
    navigate('/reservation/new')
  }
}
