import { describe, expect, it } from 'vitest'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import type { ApiError } from '@/api'

const err = (status: number | null, message: string): ApiError => ({ status, message })

describe('apiErrorMessage', () => {
  it('null/undefined için jenerik yedek metin döner', () => {
    expect(apiErrorMessage(null)).toMatch(/beklenmeyen bir hata/i)
    expect(apiErrorMessage(undefined)).toMatch(/beklenmeyen bir hata/i)
  })

  it('yanıt gelmeyen (status yok) ağ/zaman aşımı hatasını bağlantı mesajına çevirir', () => {
    expect(apiErrorMessage(err(null, 'Network Error'))).toMatch(/ulaşılamadı/i)
    expect(apiErrorMessage(err(null, 'timeout of 5000ms exceeded'))).toMatch(/ulaşılamadı/i)
  })

  it('backend anlamlı bir mesaj döndürdüyse onu korur (5xx dahil)', () => {
    expect(apiErrorMessage(err(500, 'Sunucu hatası'))).toBe('Sunucu hatası')
    expect(apiErrorMessage(err(400, 'Giriş tarihi çıkıştan sonra olamaz.'))).toBe(
      'Giriş tarihi çıkıştan sonra olamaz.',
    )
  })

  it('axios jenerik "Request failed..." metnini statüye göre yönlendirici metinle değiştirir', () => {
    expect(apiErrorMessage(err(500, 'Request failed with status code 500'))).toMatch(
      /sunucuda bir sorun/i,
    )
    expect(apiErrorMessage(err(503, ''))).toMatch(/sunucuda bir sorun/i)
  })

  it('429 ve zaman aşımı statülerine özel metin verir', () => {
    expect(apiErrorMessage(err(429, 'Request failed with status code 429'))).toMatch(
      /çok fazla istek/i,
    )
    expect(apiErrorMessage(err(504, ''))).toMatch(/zaman aşımı/i)
    expect(apiErrorMessage(err(408, ''))).toMatch(/zaman aşımı/i)
  })
})
