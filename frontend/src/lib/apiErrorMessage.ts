import type { ApiError } from '@/api'

/**
 * `ApiError`'ı kullanıcıya gösterilebilir, anlaşılır bir Türkçe mesaja çevirir
 * (PPMO K24 — arama başarısızlığında net bilgilendirme).
 *
 * Kural: backend anlamlı bir mesaj döndürdüyse onu KORU; yalnız kullanıcıya hiçbir
 * şey ifade etmeyen teknik/taşıma katmanı metinlerini ("Network Error", "timeout of
 * 0ms exceeded", axios'un "Request failed with status code 500"i) yönlendirici bir
 * metinle değiştir. Böylece TourVisio/AI çağrısı saniyeler sürüp koptuğunda kullanıcı
 * "ne oldu, ne yapmalıyım" sorusuna yanıt görür.
 */
export function apiErrorMessage(error: ApiError | null | undefined): string {
  const fallback = 'Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.'
  if (!error) return fallback

  const { status } = error
  const raw = (error.message ?? '').trim()

  // Sunucuya hiç ulaşılamadı (ağ kopması / zaman aşımı / istek iptali): HTTP yanıtı
  // gelmediğinden status yok. Bu durumda ham mesaj her zaman axios taşıma metnidir.
  if (status === null) {
    return 'Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.'
  }

  // Backend gövdesinde okunur bir mesaj varsa (ör. doğrulama/iş kuralı hatası) onu göster.
  // "Request failed with status code N" ve boş metin = axios'un jenerik teknik metni.
  const isTechnical = raw === '' || /^request failed with status code \d+$/i.test(raw)
  if (!isTechnical) return raw

  if (status === 429) {
    return 'Kısa sürede çok fazla istek gönderildi. Lütfen biraz bekleyip tekrar deneyin.'
  }
  if (status === 408 || status === 504) {
    return 'Arama zaman aşımına uğradı. Lütfen tekrar deneyin.'
  }
  if (status >= 500) {
    return 'Sunucuda bir sorun oluştu. Lütfen birazdan tekrar deneyin.'
  }
  if (status === 404) {
    return 'İstediğiniz içerik bulunamadı.'
  }
  return fallback
}
