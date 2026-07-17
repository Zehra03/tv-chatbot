import { describe, expect, it } from 'vitest'
import { COUNTRIES, dialCodeOf, findCountry, isCountryCode, nsnRange } from '@/lib/countries'

/**
 * Ülke listesinin bütünlük testleri. Liste uyruk seçiminin TEK kaynağı ve `nationalitySchema`
 * gerçek bir ISO ülkesi dayattığı için, atlanan bir satır o ülkenin vatandaşını rezervasyondan
 * tamamen dışlar — sessizce. (Şili böyle düşmüştü: Güney Amerika'nın tamamı listedeyken CL yoktu.)
 */
describe('COUNTRIES — bütünlük', () => {
  it('Şili (CL) listede ve doğru arama koduyla', () => {
    expect(isCountryCode('CL')).toBe(true)
    expect(findCountry('CL')?.name).toBe('Şili')
    expect(dialCodeOf('CL')).toBe('+56')
    expect(nsnRange('CL')).toEqual([9, 9])
  })

  it('Güney Amerika ülkelerinin tamamı listede', () => {
    const southAmerica = ['AR', 'BO', 'BR', 'CL', 'CO', 'EC', 'GY', 'PE', 'PY', 'SR', 'UY', 'VE']
    const missing = southAmerica.filter((code) => !isCountryCode(code))
    expect(missing).toEqual([])
  })

  it('ISO kodları benzersiz — kopya satır yok', () => {
    const codes = COUNTRIES.map((c) => c.code)
    expect(new Set(codes).size).toBe(codes.length)
  })

  it('her satır geçerli: 2 harfli büyük kod, dolu ad, yalnız rakamdan oluşan arama kodu', () => {
    const broken = COUNTRIES.filter(
      (c) => !/^[A-Z]{2}$/.test(c.code) || !c.name.trim() || !/^\d{1,4}$/.test(c.dial),
    )
    expect(broken).toEqual([])
  })

  it('NSN aralıkları tutarlı: min <= max ve E.164 15 hane sınırında', () => {
    const broken = COUNTRIES.filter((c) => {
      if (!c.nsn) return false
      const [min, max] = c.nsn
      return min > max || min < 1 || max + c.dial.length > 15
    })
    expect(broken).toEqual([])
  })

  it('Türkçe alfabetik sıralı (Ş, S’den sonra gelir)', () => {
    const names = COUNTRIES.map((c) => c.name)
    const sorted = [...names].sort((a, b) => a.localeCompare(b, 'tr'))
    expect(names).toEqual(sorted)
  })
})
