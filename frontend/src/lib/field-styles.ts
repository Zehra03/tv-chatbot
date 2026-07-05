/**
 * Koyu (gece uçuşu) yüzeydeki form alanları için ortak sınıflar — Input ve
 * NativeSelect'in açık varsayılanlarının üzerine cam görünüm giydirir.
 * [color-scheme:dark]: native tarih/sayı kontrollerinin ikonları koyu temada
 * görünür kalır; select açılır listesi de koyu render edilir (Chromium).
 */
export const darkFieldClass =
  'border-white/15 bg-white/5 text-white placeholder:text-white/40 focus-visible:ring-brand-teal [color-scheme:dark]'

/**
 * Skyscanner tarzı hero arama alanı: fotoğraf üzerinde beyaz, yüksek (h-12)
 * alan. [color-scheme:light]: beyaz zemin üzerindeki native tarih/sayı
 * kontrolleri açık temada kalır (koyu bölge 'dark' sınıfından etkilenmez).
 */
export const heroFieldClass =
  'h-12 rounded-lg border-transparent bg-white text-slate-900 shadow-md placeholder:text-slate-400 focus-visible:ring-2 focus-visible:ring-brand-teal [color-scheme:light]'
