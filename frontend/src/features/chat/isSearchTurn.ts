import type { PartialCriteria } from '@/types'

/**
 * Uçuştaki chat isteğinin gerçek bir otel/uçuş araması mı yoksa sıradan sohbet
 * (selamlaşma, slot-filling sorusu) mı olduğunu biriken kriterlerden çıkarır
 * (PPMO K24 — "Arıyorum…" yalnız aramada görünsün, normal sohbette değil).
 *
 * Frontend isteği göndermeden önce backend'in arayıp aramayacağını KESİN bilemez;
 * ama slot-filling durumundan güvenilir bir çıkarım yapabilir:
 *  - Kriterler tam (eksik = 0) → yeni mesaj bir (yeniden) aramadır.
 *  - Tek zorunlu alan eksik ve asistan tam onu soruyor (pendingQuestion) → bu yanıt
 *    kriterleri tamamlar, arama bu turda başlar (ilk aramayı da yakalar).
 *  - 2+ eksik veya intent yok → hâlâ konuşma/slot-filling → arama DEĞİL.
 *
 * Tek kaçırdığı durum: tüm kriterlerin tek mesajda verildiği ilk arama (kriterler
 * istek anında henüz state'te yok) — bu turda gösterge çıkmaz; ender ve zararsız.
 */
export function isSearchTurn(
  criteria: PartialCriteria | undefined,
  pendingQuestion: string | undefined,
): boolean {
  if (!criteria) return false
  const missing = missingRequiredCount(criteria)
  if (missing === 0) return true
  // Son eksik alan soruluyorsa, gelen yanıt kriterleri tamamlayıp aramayı tetikler.
  if (missing === 1 && Boolean(pendingQuestion)) return true
  return false
}

/** Aramayı başlatmak için hâlâ eksik olan zorunlu alan sayısı (varsayılanı olanlar sayılmaz). */
function missingRequiredCount(pc: PartialCriteria): number {
  if (pc.intent === 'hotel') {
    const c = pc.criteria
    let n = 0
    if (!c.destination) n++
    if (!c.checkIn) n++
    // Süre ya çıkış tarihiyle ya da gece sayısıyla verilebilir — biri yeterli.
    if (!c.checkOut && !c.nights) n++
    if (!c.adults) n++
    return n
  }
  const c = pc.criteria
  let n = 0
  if (!c.origin) n++
  if (!c.destination) n++
  if (!c.departDate) n++
  if (!c.passengers) n++
  // Dönüş tarihi yalnız gidiş-dönüşte zorunlu; tek yönde (veya tripType belirsizken) sayılmaz.
  if (c.tripType === 'round_trip' && !c.returnDate) n++
  return n
}
