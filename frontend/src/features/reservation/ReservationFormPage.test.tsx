import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { server } from '@/mocks/server'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import reservationDraftReducer, {
  type ReservationDraft,
} from '@/features/reservation/reservationDraftSlice'
import uiReducer from '@/features/ui/uiSlice'
import { ReservationFormPage } from '@/features/reservation/ReservationFormPage'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const hotelSnapshot = {
  hotelName: 'MOCK Grand Antalya Resort',
  region: 'Antalya',
  stars: 5,
  boardType: 'AI',
  checkIn: '2026-08-01',
  checkOut: '2026-08-05',
  rooms: 1,
  adults: 1,
  children: 0,
  nationality: 'TR',
  price: 1200,
  currency: 'EUR',
}

const makeHotelDraft = (
  offerId = 'off-htl-mock-001',
  hotelOverrides: Partial<typeof hotelSnapshot> = {},
): ReservationDraft => ({
  productType: 'hotel',
  offerId,
  title: 'MOCK Grand Antalya Resort',
  summary: 'Antalya · 5★ · AI',
  price: 1200,
  currency: 'EUR',
  childAges: [],
  hotel: { ...hotelSnapshot, ...hotelOverrides },
})

const hotelDraft: ReservationDraft = makeHotelDraft()

/** Yönlendirme hedefi işaretçisi — kesin onay sonrası detay sayfasına gidildiğini doğrular. */
function DetailMarker() {
  const { id } = useParams()
  return <div>Rezervasyon detayı #{id}</div>
}

function renderPage(draft: ReservationDraft | null = hotelDraft) {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      reservationDraft: reservationDraftReducer,
      ui: uiReducer,
    },
    preloadedState: { reservationDraft: { draft } },
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/reservation/new']}>
          <Routes>
            <Route path="/reservation/new" element={<ReservationFormPage />} />
            {/* Kesin onay başarıda buraya yönlendirir (created → detay, fallback → liste). */}
            <Route path="/reservations/:id" element={<DetailMarker />} />
            <Route path="/reservations" element={<div>Rezervasyon listesi</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  )
  return { store, queryClient }
}

describe('ReservationFormPage', () => {
  it('taslak yokken yönlendirme mesajı gösterir', () => {
    renderPage(null)
    expect(screen.getByText(/Önce bir ürün seçmelisiniz/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Sohbete git' })).toBeTruthy()
  })

  it('ürün özeti taslaktan gelir', () => {
    renderPage()
    expect(screen.getByText('MOCK Grand Antalya Resort')).toBeTruthy()
    expect(screen.getByText('Antalya · 5★ · AI')).toBeTruthy()
    expect(screen.getByText(/1\.200/)).toBeTruthy()
  })

  it('boş gönderimde Zod hataları gösterilir', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findAllByText('En az 2 karakter girin')).toHaveLength(2)
    expect(screen.getByText('E-posta girin')).toBeTruthy()
    expect(screen.getByText('Telefon numarası girin')).toBeTruthy()
  })

  it('bozuk e-posta biçimi hata verir', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('E-posta'), 'zehra@')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findByText('Geçerli bir e-posta girin')).toBeTruthy()
  })

  it('geçersiz yaş hata verir', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Yaş (opsiyonel)'), '200')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findByText('Geçerli bir yaş girin (18–120)')).toBeTruthy()
  })

  it('ad alanı harf dışı karakter kabul etmez', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Ad'), 'Zehra42')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(
      await screen.findByText('Yalnızca harf, boşluk ve - kısaltma işaretleri kullanın'),
    ).toBeTruthy()
  })

  it('uyruk seçilmeden gönderilemez; seçim listeden yapılır', async () => {
    const user = userEvent.setup()
    // Aramada uyruk yoksa alan boş açılır ve seçim zorunlu olur.
    renderPage(makeHotelDraft('off-htl-mock-001', { nationality: '' }))

    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findByText('Uyruk seçin')).toBeTruthy()

    await user.click(screen.getByLabelText('Uyruk'))
    await user.type(screen.getByLabelText('Ülke ara'), 'Almanya')
    await user.click(screen.getByRole('option', { name: /Almanya/ }))
    expect(screen.queryByText('Uyruk seçin')).toBeNull()
  })

  it('uyruk değişince telefon ülke kodu kendiliğinden güncellenir', async () => {
    const user = userEvent.setup()
    renderPage()

    // Arama 'TR' taşıyor → kod +90 açılır.
    expect(screen.getByLabelText('Telefon ülke kodu').textContent).toContain('+90')

    await user.click(screen.getByLabelText('Uyruk'))
    await user.type(screen.getByLabelText('Ülke ara'), 'Almanya')
    await user.click(screen.getByRole('option', { name: /Almanya/ }))

    await waitFor(() =>
      expect(screen.getByLabelText('Telefon ülke kodu').textContent).toContain('+49'),
    )
  })

  it('telefon numarası ülkenin hane kuralına uymalı', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Telefon'), '555')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findByText(/Türkiye için telefon numarası 10 haneli olmalı/)).toBeTruthy()
  })

  it('telefona ülke kodu yapıştırılırsa ulusal numaraya indirgenir', async () => {
    const user = userEvent.setup()
    renderPage()

    const phone = screen.getByLabelText('Telefon') as HTMLInputElement
    await user.click(phone)
    await user.paste('+90 (555) 111 22 33')
    expect(phone.value).toBe('5551112233')

    // Baştaki trunk 0'ı da düşer.
    await user.clear(phone)
    await user.paste('0555 111 22 33')
    expect(phone.value).toBe('5551112233')
  })

  it('ülke kodu numaranın içine yazılırsa (doğru uzunlukta olsa da) reddedilir', async () => {
    const user = userEvent.setup()
    renderPage()

    // '+90…' elle yazılınca alan '9055511122'de takılır: 10 hane olduğu için uzunluk kuralından
    // geçer ama Türkiye numarası 9 ile başlamaz — sessizce yanlış numara gitmemeli.
    await user.type(screen.getByLabelText('Telefon'), '+905551112233')
    expect((screen.getByLabelText('Telefon') as HTMLInputElement).value).toBe('9055511122')

    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    expect(await screen.findByText(/Türkiye numarası 2 ile 5 arasında bir hane ile başlar/)).toBeTruthy()
  })

  it('geçerli form → önizleme; onay checkbox işaretlenmeden gönderilemez, işaretlenince POST atılır', async () => {
    const requests: string[] = []
    server.events.on('request:start', ({ request }) => {
      requests.push(`${request.method} ${new URL(request.url).pathname}`)
    })

    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Ad'), 'Zehra')
    await user.type(screen.getByLabelText('Soyad'), 'Yılmaz')
    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Telefon'), '5551112233')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))

    // Önizleme: dondurulan toplam + misafir adı listesi (passengerNames).
    expect(await screen.findByText(/Toplam:/, {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText(/1\.200/)).toBeTruthy()
    expect(screen.getByText('Zehra Yılmaz')).toBeTruthy()

    // Açık onay: checkbox işaretlenmeden buton devre dışı.
    const submit = screen.getByRole('button', { name: 'Rezervasyonu onayla' })
    expect((submit as HTMLButtonElement).disabled).toBe(true)
    await user.click(screen.getByRole('checkbox'))
    expect((submit as HTMLButtonElement).disabled).toBe(false)

    await user.click(submit)
    await waitFor(() => expect(requests).toContain('POST /api/v1/reservations'))
    server.events.removeAllListeners()
  })

  async function fillAndPreview(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Ad'), 'Zehra')
    await user.type(screen.getByLabelText('Soyad'), 'Yılmaz')
    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Telefon'), '5551112233')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))
    await screen.findByText(/Toplam:/, {}, { timeout: 3000 })
  }

  it('başarıda detay sayfasına yönlendirir, liste invalidate edilir, taslak temizlenir', async () => {
    const user = userEvent.setup()
    const { store, queryClient } = renderPage()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    await fillAndPreview(user)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Rezervasyonu onayla' }))

    // Kesin onay başarılı → kullanıcı rezervasyonun kalıcı (URL'li) detay sayfasına taşınır;
    // artık geçici satır-içi başarı ekranına bağlı değil (canlı backend'de düşebiliyordu).
    expect(
      await screen.findByText(/Rezervasyon detayı #\d+/, {}, { timeout: 3000 }),
    ).toBeTruthy()

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reservations'] })
    expect(store.getState().reservationDraft.draft).toBeNull()
  })

  it('backend hatasında başarısızlık ekranı gösterilir', async () => {
    server.use(
      http.post('/api/v1/reservations', () =>
        HttpResponse.json({ message: 'Kontenjan kalmadı' }, { status: 409 }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await fillAndPreview(user)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Rezervasyonu onayla' }))

    expect(await screen.findByText('Rezervasyon oluşturulamadı', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('Kontenjan kalmadı')
    expect(screen.getByRole('button', { name: 'Önizlemeye dön' })).toBeTruthy()
  })

  it('uyarı (çift rezervasyon) gelince ikinci onay istenir; "Yine de onayla" detaya götürür', async () => {
    const user = userEvent.setup()
    // offerId 'OFFER-DUP' → mock 200 NeedsConfirmationResponse döndürür (uyarı dalı).
    renderPage(makeHotelDraft('OFFER-DUP'))

    await fillAndPreview(user)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Rezervasyonu onayla' }))

    // Uyarı ekranı: sağlayıcı uyarısı + ikinci açık onay butonu.
    expect(await screen.findByText('Onayınız gerekiyor', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText(/DuplicateReservationFound/)).toBeTruthy()

    await user.click(screen.getByRole('button', { name: 'Yine de onayla' }))
    expect(
      await screen.findByText(/Rezervasyon detayı #\d+/, {}, { timeout: 3000 }),
    ).toBeTruthy()
  })

  /**
   * K21: TourVisio önizleme kurulurken canlı fiyatı yeniden okur. Fiyat oynadıysa kullanıcı
   * YENİ tutarı görmeden ve AYRICA kabul etmeden onaylayamamalı. Backend priceChanged +
   * previousAmount gönderiyordu ama frontend tipinde bu alanlar yoktu (ve mock istemcinin
   * kendi tutarını yankılıyordu) — kullanıcı %30 zamlanmış bir rezervasyonu farkı hiç
   * görmeden onaylıyordu.
   */
  it('fiyat değiştiyse eski/yeni farkı gösterir ve AYRI kabul olmadan onay göndermez', async () => {
    const user = userEvent.setup()
    // offerId 'OFFER-REPRICE' → mock canlı fiyatı %30 artırır (priceChanged: true).
    renderPage(makeHotelDraft('OFFER-REPRICE'))

    await fillAndPreview(user)

    // Fark açıkça görünür: uyarı + eski tutar (üstü çizili) + yeni tutar.
    expect(await screen.findByText('Fiyat güncellendi', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.getByText('€1.200')).toBeTruthy() // aramada görülen eski tutar
    expect(screen.getAllByText(/€1\.560/).length).toBeGreaterThan(0) // 1200 * 1.3 → canlı tutar

    // Genel onay TEK BAŞINA yetmez — fiyat kabulü ayrı.
    const [priceCheckbox, confirmCheckbox] = screen.getAllByRole('checkbox')
    await user.click(confirmCheckbox)
    const submit = () => screen.getByRole('button', { name: 'Rezervasyonu onayla' }) as HTMLButtonElement
    expect(submit().disabled).toBe(true)

    // Fiyat kabul edilince onay açılır.
    await user.click(priceCheckbox)
    expect(submit().disabled).toBe(false)
  })

  /**
   * Uyarıdan SONRA 202 (COMMIT_OUTCOME_UNKNOWN) gelirse: booking geçmiş OLABİLİR. Kullanıcıya
   * "Sonuç doğrulanıyor" gösterilmeli — uyarı ekranı değil. Regresyonda `warning` temizlenmediği
   * ve `warning` dalı `pending` dalından önce geldiği için kullanıcı "Yine de onayla"ya geri
   * düşüyordu: sonucu bilinmeyen bir rezervasyonu ikinci kez onaylamaya davet.
   */
  it('uyarıdan sonra gelen 202 belirsiz sonucu gösterir — ikinci onaya geri DÜŞMEZ', async () => {
    const user = userEvent.setup()
    renderPage(makeHotelDraft('OFFER-DUP'))

    await fillAndPreview(user)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Rezervasyonu onayla' }))
    expect(await screen.findByText('Onayınız gerekiyor', {}, { timeout: 3000 })).toBeTruthy()

    // İkinci onay bu kez 202 döner (TourVisio yanıtsız kaldı).
    server.use(
      http.post('*/api/v1/reservations', () =>
        HttpResponse.json(
          { outcome: 'COMMIT_OUTCOME_UNKNOWN', message: 'Sonuç doğrulanamadı.' },
          { status: 202 },
        ),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Yine de onayla' }))

    expect(await screen.findByText('Sonuç doğrulanıyor', {}, { timeout: 3000 })).toBeTruthy()
    expect(screen.queryByText('Onayınız gerekiyor')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Yine de onayla' })).toBeNull()
    expect(screen.getByRole('link', { name: 'Rezervasyonlarım' })).toBeTruthy()
  })

  it('geçersiz snapshot (oda > yetişkin) istemcide uyarır, önizleme isteği gönderilmez', async () => {
    const requests: string[] = []
    server.events.on('request:start', ({ request }) => {
      requests.push(`${request.method} ${new URL(request.url).pathname}`)
    })
    const badDraft: ReservationDraft = {
      productType: 'hotel',
      offerId: 'off-htl-mock-001',
      title: 'MOCK Grand Antalya Resort',
      summary: 'Antalya · 5★ · AI',
      price: 1200,
      currency: 'EUR',
      childAges: [],
      hotel: { ...hotelSnapshot, rooms: 2, adults: 1 },
    }
    const user = userEvent.setup()
    renderPage(badDraft)

    await user.type(screen.getByLabelText('Ad'), 'Zehra')
    await user.type(screen.getByLabelText('Soyad'), 'Yılmaz')
    await user.type(screen.getByLabelText('E-posta'), 'zehra@example.com')
    await user.type(screen.getByLabelText('Telefon'), '5551112233')
    await user.click(screen.getByRole('button', { name: 'Önizlemeye geç' }))

    // Uyarı görünür ve backend'e hiç önizleme isteği çıkmaz (ham 400'den önce yakalanır).
    expect(
      await screen.findByText(/Oda sayısı yetişkin sayısından fazla olamaz/),
    ).toBeTruthy()
    expect(requests).not.toContain('POST /api/v1/reservations/preview')
    server.events.removeAllListeners()
  })
})
