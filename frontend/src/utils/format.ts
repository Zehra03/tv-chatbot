/**
 * Paylaşılan biçimlendiriciler (docs/frontend-architecture.md §4). Değerler
 * backend'den geldiği gibi gösterilir — fiyat/uygunluk burada asla üretilmez.
 */

/** 1200 + EUR → "€1.200" (tr-TR). Bilinmeyen kod para birimiyle düz döner. */
export function formatPrice(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat('tr-TR', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount)
  } catch {
    return `${amount} ${currency}`
  }
}

/** ISO instant → "1 Ağu 2026 11:00" (tr-TR, yerel saat). Geçersiz girdi olduğu gibi döner. */
export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('tr-TR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

/** ISO gün (YYYY-MM-DD) → "1 Ağu 2026" (tr-TR). Geçersiz girdi olduğu gibi döner. */
export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('tr-TR', { dateStyle: 'medium' }).format(date)
}
