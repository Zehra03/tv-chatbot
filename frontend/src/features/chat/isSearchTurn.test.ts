import { describe, expect, it } from 'vitest'
import { isSearchTurn } from '@/features/chat/isSearchTurn'
import type { PartialCriteria } from '@/types'

const hotel = (c: object): PartialCriteria => ({ intent: 'hotel', criteria: c }) as PartialCriteria
const flight = (c: object): PartialCriteria => ({ intent: 'flight', criteria: c }) as PartialCriteria

describe('isSearchTurn', () => {
  it('kriter yoksa (selam / intent belirsiz) arama değildir', () => {
    expect(isSearchTurn(undefined, undefined)).toBe(false)
    expect(isSearchTurn(undefined, 'Otel mi uçuş mu?')).toBe(false)
  })

  it('birden çok zorunlu alan eksikken (erken slot-filling) arama değildir', () => {
    // Sadece şehir var; tarih + kişi eksik → asistan hâlâ soruyor.
    expect(isSearchTurn(hotel({ destination: 'Antalya' }), 'Giriş tarihi nedir?')).toBe(false)
  })

  it('son zorunlu alan sorulurken (yanıt kriteri tamamlayacak) aramadır — ilk aramayı yakalar', () => {
    // Tarihler tam, yalnız kişi eksik ve asistan onu soruyor → gelen yanıt arar.
    expect(
      isSearchTurn(
        hotel({ destination: 'Antalya', checkIn: '2026-08-01', checkOut: '2026-08-05' }),
        'Kaç kişi?',
      ),
    ).toBe(true)
  })

  it('tek eksik olsa da asistan bir şey sormuyorsa arama saymaz (temkinli)', () => {
    expect(
      isSearchTurn(
        hotel({ destination: 'Antalya', checkIn: '2026-08-01', checkOut: '2026-08-05' }),
        undefined,
      ),
    ).toBe(false)
  })

  it('kriterler tamsa (yeniden arama / daraltma) aramadır', () => {
    expect(
      isSearchTurn(
        hotel({ destination: 'Antalya', checkIn: '2026-08-01', checkOut: '2026-08-05', adults: 2 }),
        undefined,
      ),
    ).toBe(true)
  })

  it('otelde çıkış yerine gece sayısı verilmişse süre tam sayılır', () => {
    expect(
      isSearchTurn(hotel({ destination: 'Antalya', checkIn: '2026-08-01', nights: 4, adults: 2 }), undefined),
    ).toBe(true)
  })

  it('uçuşta tam kriter aramadır; gidiş-dönüşte dönüş tarihi eksikse değildir', () => {
    expect(
      isSearchTurn(
        flight({ origin: 'IST', destination: 'AYT', departDate: '2026-08-01', adults: 1 }),
        undefined,
      ),
    ).toBe(true)
    expect(
      isSearchTurn(
        flight({
          origin: 'IST',
          destination: 'AYT',
          departDate: '2026-08-01',
          adults: 1,
          tripType: 'round_trip',
        }),
        'Dönüş tarihi nedir?',
      ),
    ).toBe(true) // tek eksik (dönüş) + soru → tamamlayacak tur
  })
})
