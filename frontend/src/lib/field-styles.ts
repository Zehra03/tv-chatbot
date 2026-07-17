/**
 * Form alanlarının ortak sınıfı — Input ve NativeSelect'in açık varsayılanlarının
 * üzerine cam görünüm giydirir.
 *
 * Renkler .pax-field reçetesinden (index.css) gelir ve temayla döner; koyu temadaki
 * değerler eskiden burada sabit yazan `border-white/15 bg-white/5 text-white
 * placeholder:text-white/40` ile BİREBİR aynıdır.
 *
 * `[color-scheme:dark]` kaldırıldı: artık :root/.dark üzerindeki `color-scheme`
 * miras alınıyor, native tarih/select kontrolleri temayı kendiliğinden izliyor.
 */
export const fieldClass = 'pax-field focus-visible:ring-brand-teal'

/**
 * @deprecated `fieldClass` kullanın. Ad artık yanıltıcı — sınıf koyuya özel değil,
 * tema-duyarlı. Çağrı yerleri boyama fazında geçirilecek.
 */
export const darkFieldClass = fieldClass

/**
 * Skyscanner tarzı hero arama alanı: fotoğraf üzerinde beyaz, yüksek (h-12) alan.
 * TEMADAN BAĞIMSIZ ve öyle kalmalı — fotoğrafın üstünde duruyor, zemini tema değil
 * görsel belirliyor. [color-scheme:light]: beyaz zemindeki native tarih/sayı
 * kontrolleri koyu temada da açık kalsın.
 */
export const heroFieldClass =
  'h-12 rounded-lg border-transparent bg-white text-slate-900 shadow-md placeholder:text-slate-400 focus-visible:ring-2 focus-visible:ring-brand-teal [color-scheme:light]'
