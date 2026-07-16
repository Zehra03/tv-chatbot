import { beforeAll, describe, expect, it } from 'vitest'
import { webcrypto } from 'node:crypto'
import {
  claimSeedSessions,
  deleteSessionState,
  getSessionState,
  listSessionStates,
  processMessage,
} from './chatEngine'

/**
 * MSW mock chatEngine'in kimlik kapsaması — gerçek backend ChatCaller/ChatSessionQueryService
 * paritesi: bir kimliğin (üye ya da misafir) oturumu ASLA başka kimliğe görünmez/erişilmez.
 * Regresyon: eski mock tüm oturumları tek global listeden döndürüyordu; çıkış → misafir
 * akışında önceki üyenin geçmişi sızıyordu.
 */
// jsdom crypto.randomUUID sağlamayabilir; processMessage yeni oturum id'si için kullanır.
beforeAll(() => {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
  }
})

describe('chatEngine kimlik kapsaması', () => {
  it('bir kimliğin oturumu başka kimliğin listesinde/erişiminde görünmez (IDOR yok)', () => {
    const member = 'user:m-1'
    const guest = 'guest:g-1'

    const { sessionId } = processMessage(undefined, 'Antalya oteli istiyorum', member)

    // Üye kendi oturumunu listede ve id ile görür.
    expect(listSessionStates(member).some((s) => s.id === sessionId)).toBe(true)
    expect(getSessionState(sessionId, member)).toBeDefined()

    // Misafir ne listede görür ne de id ile erişebilir ne de silebilir.
    expect(listSessionStates(guest).some((s) => s.id === sessionId)).toBe(false)
    expect(getSessionState(sessionId, guest)).toBeUndefined()
    expect(deleteSessionState(sessionId, guest)).toBe(false)
    // Silme reddedildi → üye oturumu hâlâ duruyor.
    expect(getSessionState(sessionId, member)).toBeDefined()
  })

  it('başka kimliğin id\'si verilse bile oturum devralınmaz — yeni oturum açılır', () => {
    const a = 'user:a'
    const b = 'guest:b'
    const first = processMessage(undefined, 'Antalya oteli', a)
    const second = processMessage(first.sessionId, 'İstanbul uçuşu', b)

    expect(second.sessionId).not.toBe(first.sessionId)
    expect(getSessionState(first.sessionId, a)).toBeDefined()
    expect(listSessionStates(b).map((s) => s.id)).toEqual([second.sessionId])
  })

  it('tohum demo oturumları yalnız onları devralan üyeye görünür, misafire görünmez', () => {
    const member = 'user:seed-claimer'
    const guest = 'guest:no-seed'

    // Devir öncesi tohumlar hiçbir çalışma-zamanı kimliğine görünmez.
    expect(listSessionStates(member)).toHaveLength(0)

    claimSeedSessions(member)
    expect(listSessionStates(member).length).toBeGreaterThan(0)
    // Misafir devralınan demo geçmişini görmez → "üye geçmişi misafire sızıyor" tekrar etmez.
    expect(listSessionStates(guest)).toHaveLength(0)
  })
})
