# Booking Frontend — UI/UX Denetim Raporu (Faz 0)

> Spec (`Booking Frontend UI/UX Revizyon Spesifikasyonu`) §0 gereği **önce denetim**.
> Bu belge kod değiştirmeden mevcut durumu §1–§13 için işaretler ve boşlukları listeler.
> Bağlam: PaxAssist genel bir booking sitesi **değil** — TourVisio'ya bağlı, AI-sohbet
> güdümlü otel/uçuş arama uygulaması. "Chatbot asla rezervasyon yapmaz"; booking ödeme
> içermeyen kontrollü bir forma gider. Frontend yakın zamanda düz/açık-tema öncelikli bir
> tasarım sistemine geçti (marka paleti: navy/blue/orange; `src/index.css`'te HSL semantic
> token'lar → Tailwind); bu sistem `frontend/CLAUDE.md`'de "single source of truth".

## Özet tablo

| § | Bölüm | Durum | Not |
|---|---|---|---|
| 1 | Design token katmanı | **Kısmen** | Token var ama `index.css`'te (spec `src/styles/tokens.css` ister); spacing/shadow/font CSS var değil; lint guard yok; 1 ham hex |
| 2 | Layout & grid | **Kısmen** | Tek kolon liste (spec'in 3-kolon filtre + liste + harita gridi yok — harita yok) |
| 3 | Tipografi | **Kısmen** | Inter + `Intl` merkezî ✓; `tabular-nums` fiyat/takvimde yok; `latin-ext` subset yok |
| 4 | Renk | **Kısmen** | Tek primary + CTA ayrımı ✓; puan gösterimi iki dilde (kart mavi tek-yıldız vs detay amber `StarRating`) |
| 5 | Kart (`SearchResultCard`) | **Kısmen** | Görsel oranı zorlanmamış (CLS); rozet cap yok; hover translateY yok; fiyat gecelik/toplam ayrımı yok |
| 6 | Takvim | **Kısmen** | react-day-picker + a11y + aralık ✓; hücre fiyatı yok (veri yok → skip) |
| 7 | Arama & filtreler | **Kısmen** | Tek satır arama ✓; autocomplete gruplanmamış; seçenek sayısı/histogram yok (skip); filtreler URL'de değil (karar: Redux'ta kalsın) |
| 8 | Sonuç / detay / checkout | **Kısmen** | Progressive reveal ✓, double-submit ✓; iskelet karta uymuyor; boş-sonuç kurtarma yok; detay/ödeme yok (tasarım gereği); form blur-validasyonu yok |
| 9 | Boş/yükleme/hata + motion | **Uyumlu (küçük boşluk)** | 4 durum + `prefers-reduced-motion` ✓; iskelet karta uymuyor |
| 10 | Erişilebilirlik | **Uyumlu** | `:focus-visible`, aria (combobox/takvim/people-picker), 44px hedef ✓ |
| 11 | Performans | **Kısmen** | `loading=lazy` ✓; görselde boyut/oran yok (CLS); virtualization/harita code-split gereksiz (kısa liste/harita yok) |
| 12 | Mimari & state | **Uyumlu (küçük boşluk)** | Server data Redux'ta değil ✓, fiyat backend'de doğrulanıyor ✓; `keepPreviousData` yok; arama/filtre URL'de değil (karar) |
| 13 | Yasaklılar (dark pattern) | **Uyumlu** | Hiçbir dark pattern yok ✓; kod-seviyesi: 1 ham hex, `outline:none` sorunu yok |

---

## Detaylı bulgular

### §1 — Design token katmanı · Kısmen
- **Var:** `src/index.css`'te tam token seti (HSL semantic: `--background/foreground/primary/…`,
  `--radius`, yüzey reçete değişkenleri), `.dark`/`.theme-light` varyantları, AA-kontrast
  gerekçeleri yorumlarda. `tailwind.config.js` `theme.extend` bunları `hsl(var(--token))` ile
  tek kaynaktan okuyor. Marka paleti (`brand-*`) bilinçli sabit (tema-değişmez).
- **Boşluk:**
  - Spec `src/styles/tokens.css` ister; token gerçekte `index.css`'te → **konum sapması** (kabul: mevcut mimari korunur, raporda not).
  - `--space-*`, `--shadow-*`, `--font-*` **CSS değişkeni olarak yok** (spacing = Tailwind ölçeği; shadow/font = Tailwind config). Efor: **S** (opsiyonel, düşük değer).
  - **Ham hex ihlali:** `src/components/ui/button.tsx` `hover:bg-[#E85D2A]` — tek gerçek uygulanan-renk ihlali. Efor: **S**.
  - `src/App.css` Vite starter artığı (`#646cffaa` vb.), ölü kod. Efor: **S**.
  - Ham-hex'i yakalayan **lint kuralı yok** (spec §13). Efor: **S**.

### §2 — Layout & grid · Kısmen
- `/hotels`, `/flights` **tek kolon liste** (hero + filtre barı + liste). Spec'in "filtre 3-kolon
  + liste 5-6 + harita" gridi **yok** çünkü harita yok ve faceted panel yerine yatay filtre barı var.
  Bu uygulamaya uygun bir sadeleştirme → **skip & note**. Container/gutter Tailwind ile makul.

### §3 — Tipografi · Kısmen
- **Var:** Inter (`index.css` `@import`), hiyerarşi size+weight ile; `Intl` biçimleme **merkezî**
  (`src/utils/format.ts` — `formatPrice/formatDate/formatDateTime`, `tr-TR`); hiçbir component
  `toLocaleString` ile baypas etmiyor.
- **Boşluk:**
  - `tabular-nums` yalnız 5 yerde; **fiyat (`animated-price.tsx`) ve takvim hücrelerinde yok** → rakam zıplaması. Efor: **S**.
  - Google Fonts URL'lerinde **`latin-ext` subset istenmemiş** (uygulama `lang="tr"`; ı/İ/ğ/ş glyph'leri default subset müzakeresine bırakılmış). Efor: **S**.
  - Türkçe uzun metin: butonlar çoğunlukla padding tabanlı (fixed width az); genel olarak iyi.

### §4 — Renk · Kısmen
- **Var:** Tek primary (blue) + CTA (orange, yalnız aksiyon); semantic ayrı (`success/warning/destructive`
  + okunur `destructive-emphasis`). İndirim ≠ hata sorunu yok (indirim kavramı yok).
- **Boşluk:** **Puan tek dil değil** — kart `fill-primary` (mavi) tek yıldız + sayı; detay sayfası
  `StarRating` (amber, çoklu yıldız). Tek `RatingBadge`'e birleştirilmeli. Efor: **M**.

### §5 — Kart · Kısmen  *(en kritik)*
`HotelCard.tsx` / `FlightCard.tsx` (+ `compact` chat varyantı).
- **Boşluk:**
  - Görsel **oran zorlanmamış** (`h-40…sm:h-24 sm:w-32`, sabit px; `aspect-ratio` yok, width/height yok) → **CLS + liste yüksekliği oynar**. Efor: **M**.
  - **Rozet cap yok** (3'e kadar). `MAX_BADGES = 2` gerek. Efor: **S**.
  - Hover **border+shadow** (translateY yok; spec translateY ister, scale yasak). Efor: **S**.
  - Otel fiyatı **gecelik/toplam ayrımı yok** (bare `product.price`). Uçuş "toplam" yazıyor. `criteria`'dan gece türetilebilir (`buildDraft.ts addDays`). Efor: **M**.
  - Kart mini-carousel yok (tek `thumbnailFull`) → **N/A** (skip; TourVisio tek görsel).

### §6 — Takvim · Kısmen
- **Var:** `react-day-picker` v10 (grid rolleri/klavye/aria hazır), `tr` locale, aralık seçimi,
  sıfır-gece reddi, `endpointsOnly` (uçuş), Escape/outside-click. A11y iyi.
- **Boşluk / skip:** **Hücrede fiyat yok** — gün-bazlı fiyat/müsaitlik **verisi/endpoint yok**
  (arama POST'u otel listesi döner). → **skip & note**.

### §7 — Arama & filtreler · Kısmen
- **Var:** Konum+tarih+kişi **tek satır** (hero form); `LocationAutocomplete` tam WAI-ARIA combobox
  (debounce, klavye); aktif filtre çipleri (`ActiveFilterChips` + `filterChips.ts`) tek-tık kaldırma;
  filtre ≠ sıralama ayrımı (sort tek seçim, filtre çoklu); instant apply (client-side).
- **Boşluk / skip:**
  - Autocomplete **tip bazlı gruplanmamış** (düz listbox, ikon var başlık yok). Efor: **M**.
  - **Seçenek-başı sonuç sayısı yok**, **histogram yok** → **skip** (dağılım verisi türetmek kapsam dışı).
  - **Filtreler URL'de değil** (Redux `uiSlice`). **Karar: Redux'ta kalsın** (URL migrasyonu ertelendi).

### §8 — Sonuç / detay / checkout · Kısmen
- **Var:** Progressive reveal (`useProgressiveReveal`, ilk 5 + "Daha fazla"); `EmptyState/ErrorState/LoadingState`;
  rezervasyon = tek sayfa çok-adımlı state machine (`FormStepper` 1 Bilgiler·2 Önizleme·3 Sonuç),
  preview→confirm (server-frozen fiyat + re-price farkı + ayrı onay), **double-submit koruması** (client disabled+spinner, server `DUPLICATE_IN_PROGRESS`/`pending`).
- **Boşluk / skip:**
  - **İskelet karta uymuyor** (`<Skeleton h-32/>` genel blok) → CLS. Efor: **M**. *(Faz 3)*
  - **Boş-sonuç pasif** ("bulunamadı", kurtarma aksiyonu yok). Filtre sıfırlama CTA'sı eklenebilir. Efor: **S**. *(Faz 4)*
  - **Otel/uçuş detay sayfası yok** (rota yok; kart → forma) → **skip** (galeri/sticky widget/yorum/POI kapsam dışı).
  - **Ödeme/kredi kartı/misafir checkout/timer yok** → **tasarım gereği** (ödemesiz kontrollü form, giriş zorunlu) → **skip & note**.
  - Form **`autocomplete` seyrek** (yalnız telefon `tel-national`); email/ad/soyad/doğum eksik. Efor: **S**. *(Faz 5)*
  - **Validasyon blur'da değil** (RHF `mode` yok → submit'te). Efor: **S**. *(Faz 5)*
  - **Fiyat kalem-kırılımı yok** (tek "Toplam") → gecelik×gece/vergi/hizmet verisi yok → **skip**.

### §9 — Boş/yükleme/hata + motion · Uyumlu (küçük boşluk)
- **Var:** Her async view'da 4 durum; `@media (prefers-reduced-motion)` global (`index.css` + `motion-reduce:` utility'ler); geçişler kısa.
- **Boşluk:** İskelet karta uymuyor (§8). Optimistic UI kullanılmıyor (favoriler yok → gereksiz).

### §10 — Erişilebilirlik · Uyumlu
- `:focus-visible` stilleri, `outline:none`-only sorunu yok; takvim/combobox/people-picker tam aria;
  modal focus yönetimi; 44px dokunma hedefi (`min-h-[44px]`); `sr-only` fiyat/yıldız; `role="alert"` form hataları.
- İyileştirme fırsatı: yeni eklenecek hover/skeleton'lar da reduced-motion uyumlu kalmalı.

### §11 — Performans · Kısmen
- **Var:** `loading="lazy"`, `onError` placeholder.
- **Boşluk / skip:** Görselde **width/height/aspect-ratio yok** → CLS. Efor: **M** *(Faz 2 ile birlikte)*.
  Virtualization **gereksiz** (kısa liste, progressive reveal) → skip. Harita code-split **N/A** (harita yok) → skip.

### §12 — Mimari & state · Uyumlu (küçük boşluk)
- **Var:** Redux yalnız UI/client (auth, chat, reservationDraft, ui-filters); server verisi **React Query**'de;
  **hiçbir server verisi Redux'ta değil**; fiyat backend `preview` ile **doğrulanıyor** (re-price farkı kullanıcıya gösteriliyor).
- **Boşluk / karar:**
  - **`keepPreviousData`/`placeholderData` yok** → refetch'te liste iskelet'e düşüyor. **Karar kapsamında ertelendi** (URL migrasyonuyla birlikte).
  - **Arama/filtre URL'de değil** → **karar: Redux'ta kalsın**.

### §13 — Yasaklılar (dark pattern) · Uyumlu
- **Denetim hiçbir dark pattern bulmadı:** sahte kıtlık/aciliyet yok, resetlenen timer yok, drip pricing
  yok (vergi son adımda eklenmiyor — zaten kalem-kırılımı yok, tek toplam preview'da donuyor),
  confirmshaming yok, önceden-işaretli ek hizmet yok, yanıltıcı üstü-çizili fiyat yok.
- **Kod-seviyesi:** 1 ham hex (§1), `outline:none`-only yok, para/tarih `Intl` ile, spacing Tailwind ölçeğinde.

---

## Kapsam DIŞI (skip & note — kullanıcı kararı + veri/rota yokluğu)

| Kalem | § | Gerekçe |
|---|---|---|
| Otel/uçuş **detay sayfası** (galeri, sticky widget, yorum, POI) | 5, 7 | Rota yok; kart doğrudan forma gider |
| **Harita** + harita-liste senkron | 6, 10 | Map bağımlılığı/komponenti yok |
| **Ödeme/checkout** (kart, misafir, timer) | 8 | Ödemesiz kontrollü form; giriş zorunlu (tasarım) |
| Filtre **histogramı** + seçenek-başı sayı | 7 | Dağılım verisi yok |
| Fiyat **kalem-kırılımı** (gecelik×gece/vergi) | 8 | Kalem verisi yok |
| Takvim **hücre fiyatı** | 6 | Gün-bazlı fiyat endpoint'i yok |
| **URL-state** + `keepPreviousData` | 12 | Kullanıcı kararı: Redux'ta kalsın |
| **Virtualization** | 11 | Kısa liste + progressive reveal |
| **Carousel** (kartta) | 5 | TourVisio tek görsel |

## Uygulanacak boşluklar (faz haritası)

- **Faz 1:** §1 ham hex + ölü `App.css` + lint guard · §3 `latin-ext` + `tabular-nums`
- **Faz 2:** §4 `RatingBadge` · §5 görsel oran (CLS) + rozet cap + translateY hover + fiyat gecelik/toplam
- **Faz 3:** §6/§8/§11 karta birebir iskelet
- **Faz 4:** §8 boş-sonuç kurtarma aksiyonu
- **Faz 5:** §8 form `autocomplete` + blur validasyonu
- **Faz 6:** §13 dark-pattern teyidi + §15 final checklist → `docs/RAPOR.md`
