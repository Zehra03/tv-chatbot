import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  ADULT_MIN_AGE,
  CHILD_MAX_AGE,
  CHILD_MIN_AGE,
  MAX_AGE,
} from '@/features/reservation/reservationFormSchema'

/**
 * Yaş bantları sunucudan gelmiyor: form şeması backend `PassengerType` enum'ının bir KOPYASINI
 * taşıyor. Kopya sessizce ayrışırsa form, backend'in 400 ile reddedeceği bir yaşı kabul eder
 * (ya da tersi: geçerli bir yaşı reddeder) — kullanıcı çıkışsız kalır.
 *
 * Bu test backend enum'ını kaynak dosyadan okuyup sayıları karşılaştırır; bantlar orada
 * değişirse burada kırılır. Java'yı derlemez, sadece bant tanımını okur.
 */

const ENUM_PATH = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../backend/src/main/java/com/paximum/paxassist/reservation/domain/PassengerType.java',
)

/**
 * `ADULT(18, PassengerType.MAX_AGE),` → { ADULT: ['18', 'MAX_AGE'] }
 *
 * Sınır ya bir sayı ya da bir sabitin adıdır; sabit `PassengerType.` ile nitelenmiş yazılır
 * (enum sabitleri MAX_AGE bildiriminden önce geldiği için Java düz adı "illegal forward
 * reference" sayıyor), bu yüzden nokta da desene dahil ve önek atılır.
 */
function readBackendBands(source: string): Record<string, [string, string]> {
  const bands: Record<string, [string, string]> = {}
  for (const [, name, min, max] of source.matchAll(
    /^\s{4}(ADULT|CHILD|INFANT)\(\s*([\w.]+)\s*,\s*([\w.]+)\s*\)/gm,
  )) {
    bands[name] = [unqualify(min), unqualify(max)]
  }
  // Desen tutmazsa hata "undefined okunamadı" diye değil, gerçekten olan şeyle çıksın:
  // enum'ın bant yazımı değişmiş ve bu test artık hiçbir şeyi doğrulamıyor.
  for (const type of ['ADULT', 'CHILD', 'INFANT']) {
    if (!bands[type]) {
      throw new Error(
        `PassengerType.java içinde ${type} bandı okunamadı — bant yazımı değişmiş olabilir. ` +
          `Beklenen biçim: "    ${type}(<min>, <max>),". Testin desenini güncelle.`,
      )
    }
  }
  return bands
}

/** `PassengerType.MAX_AGE` → `MAX_AGE`; sayılar olduğu gibi kalır. */
function unqualify(value: string): string {
  return value.replace(/^PassengerType\./, '')
}

function readBackendMaxAge(source: string): number {
  const match = source.match(/static final int MAX_AGE = (\d+);/)
  return match ? Number(match[1]) : Number.NaN
}

describe('yaş bantları — backend PassengerType ile aynı olmalı', () => {
  // Frontend tek başına (backend checkout'u olmadan) çalıştırıldığında test anlamsız kalır.
  const available = existsSync(ENUM_PATH)
  const source = available ? readFileSync(ENUM_PATH, 'utf8') : ''

  it.runIf(available)('backend enum bant tanımı okunabiliyor', () => {
    const bands = readBackendBands(source)
    expect(Object.keys(bands).sort()).toEqual(['ADULT', 'CHILD', 'INFANT'])
  })

  it.runIf(available)('yetişkin alt sınırı ve üst yaş sınırı eşleşiyor', () => {
    const bands = readBackendBands(source)
    expect(Number(bands.ADULT[0])).toBe(ADULT_MIN_AGE)
    // Yetişkinin üst sınırı enum'da MAX_AGE sabitine bağlı.
    expect(bands.ADULT[1]).toBe('MAX_AGE')
    expect(readBackendMaxAge(source)).toBe(MAX_AGE)
  })

  it.runIf(available)('çocuk bandı eşleşiyor', () => {
    const bands = readBackendBands(source)
    expect(Number(bands.CHILD[0])).toBe(CHILD_MIN_AGE)
    expect(Number(bands.CHILD[1])).toBe(CHILD_MAX_AGE)
  })

  it.runIf(available)('bebek bandı çocuk bandının hemen altında bitiyor', () => {
    const bands = readBackendBands(source)
    expect(Number(bands.INFANT[0])).toBe(0)
    expect(Number(bands.INFANT[1])).toBe(CHILD_MIN_AGE - 1)
  })
})
