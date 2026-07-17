import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { reservationApi } from '@/api'

/**
 * İptalin 202 dalı. Backend TourVisio'dan yanıt alamayınca CANCEL_OUTCOME_UNKNOWN + 202 döner:
 * iptal geçmiş de olabilir geçmemiş de. Axios 2xx'i resolve ettiği için bu dal eskiden düz
 * `OutcomeResponse` dönüyor ve hook koşulsuz "Rezervasyon iptal edildi." diyordu — MSW mock'u
 * yalnız 200 döndürdüğünden hiçbir test bu yolu sürmüyordu.
 */

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('reservationApi.cancel — sonuç dallanması', () => {
  it('200 → kind: cancelled (kesin iptal)', async () => {
    server.use(
      http.patch('*/api/v1/reservations/:id/cancel', () =>
        HttpResponse.json({ outcome: 'CANCELLED', message: 'Reservation 1001 is now cancelled' }, { status: 200 }),
      ),
    )
    const result = await reservationApi.cancel(1001, { reason: 'r-1' })
    expect(result.kind).toBe('cancelled')
    expect(result.outcome.outcome).toBe('CANCELLED')
  })

  it('202 CANCEL_OUTCOME_UNKNOWN → kind: pending, başarı DEĞİL', async () => {
    const backendMessage =
      'İptal talebiniz alındı ancak sonucu henüz doğrulayamadık. Lütfen tekrar göndermeyin; ' +
      'birkaç dakika içinde rezervasyonunuzun durumunu kontrol edin.'
    server.use(
      http.patch('*/api/v1/reservations/:id/cancel', () =>
        HttpResponse.json({ outcome: 'CANCEL_OUTCOME_UNKNOWN', message: backendMessage }, { status: 202 }),
      ),
    )
    const result = await reservationApi.cancel(1001, { reason: 'r-1' })

    // Regresyonda burası patlar: 202 de 'cancelled' sayılıyordu.
    expect(result.kind).toBe('pending')
    expect(result.outcome.outcome).toBe('CANCEL_OUTCOME_UNKNOWN')
    // Backend'in bu senaryo için özel yazdığı metin korunmalı — hook onu gösteriyor.
    expect(result.outcome.message).toBe(backendMessage)
  })

  it('4xx/5xx reject olur (ApiError) — pending ile karışmaz', async () => {
    server.use(
      http.patch('*/api/v1/reservations/:id/cancel', () =>
        HttpResponse.json(
          { outcome: 'TOURVISIO_REJECTED', message: 'İptal talebiniz sağlayıcı tarafından reddedildi.' },
          { status: 422 },
        ),
      ),
    )
    await expect(reservationApi.cancel(1001, { reason: 'r-1' })).rejects.toMatchObject({
      status: 422,
      code: 'TOURVISIO_REJECTED',
    })
  })
})
