/**
 * Form alanlarının ortak sınıfı — Input ve NativeSelect'in açık varsayılanlarının
 * üzerine cam görünüm giydirir. Renkler `--field-*` token'larından gelir ve temayla döner;
 * koyu temadaki değerler `border-white/15 bg-white/5 text-white placeholder:text-white/40`
 * ile birebir aynıdır (bkz. index.css `.dark`).
 *
 * `[color-scheme:dark]` kaldırıldı: artık :root/.dark üzerindeki `color-scheme`
 * miras alınıyor, native tarih/select kontrolleri temayı kendiliğinden izliyor.
 *
 * NEDEN düz bir sınıf (`.pax-field`) değil de token'a bakan UTILITY'ler — bu bir regresyon
 * kaydı, üslup tercihi değil:
 * Input'un kendi tabanında `border border-input bg-transparent
 * placeholder:text-muted-foreground` var. `cn()` = clsx + tailwind-merge ve tailwind-merge
 * yalnız TANIDIĞI Tailwind sınıflarını eleyebiliyor. `.pax-field` bir utility olmadığından
 * Input'unkiler elenmiyor; ikisi de `@layer utilities`'te olduğu ve `.pax-field`
 * `@layer base`'de kaldığı için onu EZİYORLARDI. Sonuç: koyu temada kenarlık `--field-border`
 * yerine `--input` (#1e293b) ile boyanıyor ve lacivert kartın (#23395c) üstünde 1.26:1 ile
 * GÖRÜNMÜYORDU; alanın zemini de `bg-transparent`ta kalıyordu. Sabit `border-white/15`
 * yazan eski hâl çalışıyordu çünkü O bir utility'ydi ve tailwind-merge `border-input`'u
 * eliyordu — aşağısı aynı mekanizmayı token'larla geri getirir.
 *
 * `color:` ipucu ŞART: `border-[…]`/`bg-[…]` tek başına genişlik/görsel mi renk mi belirsiz
 * kalır ve tailwind-merge doğru çakışma grubunu seçemez.
 *
 * Alfa token'ın İÇİNDE sabittir (çağrı yerinde `bg-field/40` gibi seçilmez) — index.css'teki
 * yüzey reçetesi notuyla aynı gerekçe.
 */
export const fieldClass = [
  'border-[color:var(--field-border)]',
  'bg-[color:var(--field-bg)]',
  'text-[color:var(--field-fg)]',
  'placeholder:text-[color:var(--field-placeholder)]',
  'focus-visible:ring-brand-steel',
].join(' ')

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
  'h-12 rounded-lg border-transparent bg-white text-slate-900 shadow-md placeholder:text-slate-400 focus-visible:ring-2 focus-visible:ring-brand-steel [color-scheme:light]'
