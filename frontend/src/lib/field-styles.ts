/**
 * Koyu (gece uçuşu) yüzeydeki form alanları için ortak sınıflar — Input ve
 * NativeSelect'in açık varsayılanlarının üzerine cam görünüm giydirir.
 * [color-scheme:dark]: native tarih/sayı kontrollerinin ikonları koyu temada
 * görünür kalır; select açılır listesi de koyu render edilir (Chromium).
 */
export const darkFieldClass =
  'border-white/15 bg-white/5 text-white placeholder:text-white/40 focus-visible:ring-brand-teal [color-scheme:dark]'

/** Koyu yüzeydeki birincil (gradyan) eylem butonu — Seç/Ara/Gönder ailesi. */
export const darkPrimaryButtonClass =
  'rounded-full bg-gradient-to-r from-brand-blue to-brand-teal px-6 text-white shadow-[0_2px_12px_theme(colors.brand.teal/30%)] transition-shadow hover:shadow-[0_2px_20px_theme(colors.brand.teal/50%)]'
