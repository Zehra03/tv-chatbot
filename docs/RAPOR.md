# Booking Frontend — UI/UX Revizyon Final Raporu

> Spec §12 (faz planı) ve §15 (final checklist) kapanışı. Denetim `docs/audit.md`'de;
> bu belge **uygulananları**, **dark-pattern denetimini** ve **kapsam dışı** kalemleri
> gerekçeleriyle toplar. Her faz ayrı commit (spec §14 "her faz ayrı PR" kuralı).

## Bağlam ve kapsam kararları

PaxAssist genel bir booking sitesi değil — AI-sohbet güdümlü otel/uçuş arama; "chatbot asla
rezervasyon yapmaz", booking ödemesiz kontrollü forma gider. Frontend zaten düz/açık-tema
öncelikli, marka paletli (navy/blue/orange), HSL semantic token'lı bir tasarım sistemine
sahipti. Bu revizyon spec'in **bu repoda geçerli** kurallarını, mevcut sistemi ve state
mimarisini bozmadan uyguladı. Onaylı üç kapsam kararı:

1. **Marka paleti korundu** (spec'in generic-mavi `tokens.css`'i benimsenmedi).
2. **Filtreler Redux'ta kaldı** (§12 URL-state migrasyonu ertelendi).
3. **Sadece parlatma** — yeni sayfa/özellik (detay, harita, histogram, kalem-kırılımı) icat edilmedi.

## Fazlar ve uygulanan kurallar

| Faz | Commit | Uygulanan (§) |
|---|---|---|
| 0 Denetim | `d50eb83` | `docs/audit.md` — §1–§13 durum + boşluk + kapsam-dışı |
| 1 Token hijyeni | `2338623` | §1 tek ham hex → `brand-orange-hover`; ölü `App.css` silindi; §13 ESLint ham-hex guard; §3 `tabular-nums` (fiyat+takvim); §3/§11 font preconnect + latin-ext doğrulaması |
| 2 Kart sistemi | `e989fc4` | §4 tek `StarRating` (amber, role=img+aria-label) kart+detayda; §5 görsel 4:3 + width/height (CLS); §5 `motion-safe` translateY hover; §5 `MAX_BADGES=2`; §3/§5 otel fiyatı **toplam + gecelik** |
| 3 İskelet | `2da65db` | §6/§11 `HotelCardSkeleton`/`FlightCardSkeleton` — karta birebir footprint → CLS ≈ 0 |
| 4 Boş-sonuç | `08ce83b` | §6 filtre kaynaklı 0 sonuçta tıklanabilir kurtarma ("Filtreleri temizle"); `EmptyState.action` slotu |
| 5 Form a11y | `06bf6b8` | §8/§10 `autocomplete` (given-name/family-name/bday/email); RHF `mode:'onTouched'` → blur validasyonu |
| 6 Rapor | *(bu commit)* | §13 dark-pattern denetimi + §15 checklist |

**Not:** `0530d3b` (backend RoomParty) ve `da40086` (light-mode WIP) revizyon fazı değil —
temiz bir taban için commit'lenen mevcut çalışmadır (bkz. kullanıcı onayı).

Her faz sonunda **`npm run build` + `npm run lint` (0 hata) + `npm run test` (248 geçer)** yeşil.
Faz 2/3 ayrıca gerçek tarayıcıda (MSW) doğrulandı: Antalya araması → kart amber 5 yıldız,
`€1.200` + `5 gece · €240/gece`, 4:3 görsel — hepsi beklenen gibi.

## §13 Dark-pattern denetimi — TEMİZ ✓

Hedefli tarama (`defaultChecked`, `checked={true}`, `countdown`, `setInterval`, "son X oda",
"kalan", "acele", "sınırlı süre", "tükeniyor") yalnız iyi-huylu eşleşmeler döndürdü ("kalan
öğe sayısı", telefon-kodu mantığı). Kaldırılacak dark pattern **yok**:

- ❌ Sahte kıtlık / "Son 1 oda!" — yok
- ❌ Sahte aciliyet / resetlenen timer — yok (hiç geri sayım yok)
- ❌ Drip pricing — yok; preview'da tek donmuş `totalAmount`, vergi son adımda EKLENMİYOR
- ❌ Confirmshaming — yok
- ❌ Önceden işaretli ek hizmet/sigorta — yok (`confirmed`/`priceAccepted` checkbox'ları default kapalı)
- ❌ Yanıltıcı üstü-çizili fiyat — yok (üstü çizili fiyat kavramı yok; fiyat backend'den geldiği gibi)

Ek doğruluk: fiyat client'ta üretilmiyor, backend `preview`/`totalAmount` ile doğrulanıyor;
re-price farkı kullanıcıya gösterilip AYRI onay isteniyor (spec §12 "fiyatı backend doğrula").

## Kapsam dışı (skip & note)

Spec'in bu uygulamada karşılığı olmayan / veri gerektiren kuralları (spec §0 "varsayım yapma,
yeni özellik icat etme"):

| Kalem | § | Gerekçe |
|---|---|---|
| Otel/uçuş detay sayfası (galeri, sticky widget, yorum, POI) | 5,7 | Rota yok; kart → forma |
| Harita + harita-liste senkron | 6,10 | Map bağımlılığı/komponenti yok |
| Ödeme/checkout (kart, misafir, timer) | 8 | Ödemesiz kontrollü form, giriş zorunlu (tasarım) |
| Filtre histogramı + seçenek-başı sayı | 7 | Dağılım/sayım verisi yok |
| Fiyat kalem-kırılımı (gecelik×gece/vergi) | 8 | Kalem verisi yok |
| Takvim hücre fiyatı | 6 | Gün-bazlı fiyat endpoint'i yok |
| URL-state + `keepPreviousData` | 12 | Kullanıcı kararı: Redux'ta kalsın |
| Virtualization | 11 | Kısa liste + `useProgressiveReveal` |
| Kart carousel | 5 | TourVisio tek görsel (`thumbnailFull`) |
| Autocomplete tip-gruplama | 7 | İyileştirme; combobox a11y tam, gruplama ertelendi |

## §15 Final checklist (bu repoda geçerli maddeler)

**Token & tasarım**
- [x] Bileşenlerde uygulanan ham hex yok (ESLint guard zorluyor; kalanlar yorum/marka kaynağı/Design swatch)
- [x] Spacing 4/8px tabanlı (Tailwind ölçeği)
- [x] Tek birincil aksiyon rengi (CTA turuncu); indirim ≠ hata (indirim kavramı yok)
- [x] Radius sistemli; kart içi görsel (`rounded-xl`) < kart (`rounded-2xl`)

**Tipografi & Türkçe**
- [x] Fiyat kartın en baskın elemanlarından (sağa yaslı, bold, xl)
- [x] `tabular-nums` fiyat + takvimde aktif
- [x] latin-ext css2 unicode-range ile servis ediliyor (Ğ/Ş/İ/ı doğrulandı); preconnect eklendi
- [x] Uzun Türkçe metin butonları taşırmıyor (padding tabanlı, min-width yok)
- [x] Tüm para/tarih `Intl` (`utils/format.ts`) üzerinden

**Component**
- [x] Kart: oran 4:3 zorlanmış, ≤2 rozet, düşük elevation, `translateY` hover
- [x] Takvim: klavye + `role=grid` + aria (react-day-picker); ~ Fiyatlı hücre → veri yok (skip)
- [~] Filtreler: chip'li, sonuç-sayılı toplam var; URL/histogram/opsiyon-sayı → kapsam dışı

**Akış**
- [x] Filtre kaynaklı boş sonuçta tıklanabilir kurtarma
- [~] Fiyat kırılımı → veri yok; vergi son adımda EKLENMİYOR (tek toplam donuyor) ✓
- [x] İptal politikası detay/preview'da tam tarihle (mevcut)
- [~] Misafir checkout → tasarım gereği yok (giriş zorunlu)
- [x] Double-submit imkânsız; ödeme hatası kavramı yok, form state korunur

**A11y**
- [x] `:focus-visible`, `outline:none`-only yok; 44px hedef; aria (combobox/takvim/people)
- [x] Klavyeyle uçtan uca akış (arama→filtre→kart→form) mümkün
- [x] `prefers-reduced-motion` global; yeni hover `motion-safe` ile gated
- [x] Focus ring her yerde görünür

**Performans**
- [x] CLS: kart görselinde width/height + aspect-ratio; iskelet karta birebir
- [~] Harita code-split / virtualization → uygulanamaz (harita yok, kısa liste)

**Mimari**
- [~] Arama/filtre URL'de değil → kullanıcı kararı (Redux)
- [x] Sunucu verisi Redux'ta değil (React Query); fiyat backend'de doğrulanıyor
- [~] `keepPreviousData` → URL migrasyonuyla birlikte ertelendi

**Etik**
- [x] Hiçbir dark pattern yok

> `[x]` uygulandı · `[~]` kısmen/gerekçeli kapsam dışı. Tam §1–§13 durum tablosu: `docs/audit.md`.
