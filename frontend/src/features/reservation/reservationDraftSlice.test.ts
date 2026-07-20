import { describe, expect, it } from 'vitest'
import reducer, {
  clearDraft,
  setDraft,
  type ReservationDraft,
} from '@/features/reservation/reservationDraftSlice'
import { guestSessionStarted, logout, sessionStarted } from '@/features/auth/authSlice'

/**
 * Taslağın kimlik sınırı davranışı. İki yönlü koruma:
 *  - `logout` / `guestSessionStarted` → taslak DÜŞER (A'nın seçtiği ürün B'ye sızmasın).
 *  - `sessionStarted` → taslak KALIR (misafirin hesaba yükselmesi aynı kişinin devamı;
 *    RequireAccount kullanıcıyı /login'e yollayıp forma geri getirdiği için silinirse akış
 *    kendini imha eder).
 */

const DRAFT: ReservationDraft = {
  productType: 'hotel',
  offerId: 'offer-token-1',
  title: 'MOCK Grand Antalya Resort',
  summary: '2 yetişkin, 1 oda',
  price: 1200,
  currency: 'EUR',
  hotel: {
    hotelName: 'MOCK Grand Antalya Resort',
    checkIn: '2026-08-01',
    checkOut: '2026-08-05',
    rooms: 1,
    adults: 2,
    children: 0,
    nationality: 'TR',
    price: 1200,
    currency: 'EUR',
  },
  childAges: [],
}

const login = () =>
  sessionStarted({
    user: { id: 'u-1', email: 'a@b.co', name: 'Ada' },
    token: 't',
    refreshToken: 'r',
  })

describe('reservationDraftSlice', () => {
  it('setDraft taslağı yazar, clearDraft siler', () => {
    let state = reducer(undefined, setDraft(DRAFT))
    expect(state.draft?.offerId).toBe('offer-token-1')
    state = reducer(state, clearDraft())
    expect(state.draft).toBeNull()
  })

  it('logout taslağı düşürür — A’nın seçtiği ürün sonraki kimliğe sızmaz', () => {
    let state = reducer(undefined, setDraft(DRAFT))
    state = reducer(state, logout())
    expect(state.draft).toBeNull()
  })

  it('misafir oturumu başlangıcı taslağı düşürür', () => {
    let state = reducer(undefined, setDraft(DRAFT))
    state = reducer(state, guestSessionStarted())
    expect(state.draft).toBeNull()
  })

  it('giriş (sessionStarted) taslağı KORUR — misafir "Seç" → giriş → form turu çalışsın', () => {
    let state = reducer(undefined, setDraft(DRAFT))
    state = reducer(state, login())
    // Regresyonda burası patlar: sessionStarted matcher'daydı ve kullanıcı geri döndüğü
    // formda "Önce bir ürün seçmelisiniz" görüyordu.
    expect(state.draft).not.toBeNull()
    expect(state.draft?.offerId).toBe('offer-token-1')
  })

  it('giriş taslağı korusa da, sonraki çıkış yine siler (sızıntı kapalı kalır)', () => {
    let state = reducer(undefined, setDraft(DRAFT))
    state = reducer(state, login())
    expect(state.draft).not.toBeNull()
    state = reducer(state, logout())
    expect(state.draft).toBeNull()
  })
})
