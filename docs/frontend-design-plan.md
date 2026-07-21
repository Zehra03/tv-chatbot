# PaxAssist — Tasarım Yenileme Planı ("Gece Uçuşu")

> ⚠️ **BU PLAN ARTIK GEÇERSİZ (tarihsel kayıt).** Ürün, düz (flat), **açık-tema
> öncelikli** bir tasarıma (Booking/Stripe/Linear dili) geçti: glassmorphism, ağır
> gradyanlar, WebGL ve teal/iris/ice paleti KALDIRILDI. Güncel tasarım sistemi için
> `frontend/CLAUDE.md`'ye bakın. Aşağıdaki "Gece Uçuşu" planı yalnızca geçmiş referanstır.
>
> Güncel palet: `brand-navy #00243F · blue #004E89 · steel #1A659E · orange #FF6B35 ·
> peach #F7C59F · cream #EFEFD0`. Varsayılan tema **açık**; koyu tema desteklenen
> ikincil seçenek. Yüzeyler dolu (`bg-card` + kenarlık + `shadow-soft`), CTA turuncu.

> (Tarihsel hedef) Login/register'da kurulan görsel kimliği (lacivert + glassmorphism +
> teal/iris gradyan) uygulamanın tamamına taşımak.

## 1. Konsept: "AI alanı koyu, kontrollü alan açık"

Projenin ana güvenlik hikâyesi zaten mimaride var: **chatbot asla rezervasyon
yapmaz**, kullanıcıyı AI'sız kontrollü forma yönlendirir. Bu hikâyeyi görsel dile
çeviriyoruz — jüriye anlatılabilir, kasıtlı bir tasarım kararı:

- **AI bölgesi (login, chat, arama sonuçları)** → koyu "gece uçuşu" yüzeyi:
  `brand-navy` zemin, cam kartlar (`bg-white/10 backdrop-blur`), teal/iris vurgular,
  hareketli blob alanı (login'dekiyle aynı dil, daha düşük dozda).
- **Kontrollü bölge (rezervasyon formu + onay)** → sade, açık, "resmî" yüzey:
  mevcut açık shadcn teması. Geçiş anı (kart "Seç" → form) demoda vurgulanır:
  *"Buradan itibaren AI devre dışı — bilinçli olarak sakin bir arayüz."*

Böylece iki tema çakışmıyor; ikisi de anlam kazanıyor.

## 2. Renk / token kararları

- Marka paleti sabit kalır: `brand-navy #0B234A`, `brand-blue #2E8FFF`,
  `brand-teal #17D6C3`, `brand-iris #8B8CFF`, `brand-ice #A9E9FF`
  (tailwind.config.js'te tanımlı — ham hex kullanma).
- Koyu ekranlar `.dark` sınıfını **kullanmaz**; login'deki gibi doğrudan
  `brand-*` yüzeyleri kullanır (tema anahtarı değil, bölge kimliği).
- Cam kart reçetesi (login'den standartlaştır):
  `rounded-2xl border border-white/15 bg-white/10 backdrop-blur-md`
  → `src/index.css`'e `.glass-card` utility'si olarak çıkar, her yerde aynı kullan.

## 3. Eklenecek araçlar (npm)

| Paket | Ne için | Öncelik |
|---|---|---|
| `framer-motion` | Sayfa geçişleri, kart stagger girişleri, chat mesaj animasyonları, layout animasyonları | **Zorunlu — en yüksek etki** |
| `sonner` | Marka renkli toast'lar (rezervasyon onayı, hatalar) | Yüksek |
| `react-day-picker` + `date-fns` | Check-in/out tarih aralığı seçici (rezervasyon & otel filtreleri) | Yüksek |
| `@number-flow/react` | Fiyatların akarak değişmesi (filtre değişiminde kart fiyatları) | Orta — küçük ama jüri etkisi büyük |
| `embla-carousel-react` | Otel kartlarında görsel karuseli | Opsiyonel |

Zaten mevcut: `tailwindcss-animate`, `lucide-react`, CVA, shadcn primitifleri.

## 4. MCP'ler

Üçü de halihazırda bağlı — yeni MCP gerekmez:

- **Magic (21st.dev)** — modern/animasyonlu bileşen üretimi + ilham
  (`component_inspiration` ile önce 3-4 varyant gör, sonra `component_builder`).
- **shadcn** — temel primitifler (dialog, popover, calendar, skeleton, tabs).
- **Playwright** — `#design` playground'unu ekran görüntüsüyle doğrulama döngüsü.

Opsiyonel: hero/otel görselleri ve logo rafinesi için bir görsel-üretim MCP'si
(şu an bağlı değil; assets yeterliyse gerek yok).

## 5. Fazlar (her faz = küçük PR, `develop`'a)

### Faz 0 — Altyapı (yarım gün)
- `framer-motion`, `sonner` kur; `.glass-card` utility'sini çıkar.
- `Design.tsx` playground'una "koyu yüzey" bölümü ekle (cam kart, chip, buton
  varyantları koyu zeminde görünsün).
- FloatingInput + glow-bar'ı `components/ui/`'ye taşı (login'e gömülü kalmasın).

### Faz 1 — App Shell (Layout.tsx)
- Jenerik beyaz header → koyu cam topbar: `brand-navy` zemin üzerinde
  `backdrop-blur` bar, aktif nav linki teal alt çizgi/glow.
- Arka planda çok düşük opaklıkta blob alanı (login'in sakin versiyonu —
  `BackgroundGradientAnimation` zaten hazır, koyu renklerle parametrize et).
- Framer-motion `AnimatePresence` ile route geçiş fade/slide'ı.

### Faz 2 — Chat (demonun kalbi)
- Asistan balonları cam kart, kullanıcı balonları `brand-blue→teal` gradyan.
- Mesaj girişte `motion` stagger; yazıyor... göstergesi (üç nokta pulse).
- Kriter rozetleri (CriteriaChips) → teal glow'lu cam chip'ler.
- Composer: login input'larındaki glow-bar odak efekti.
- Inline sonuç kartları sırayla süzülerek girer (stagger 60-80ms).

### Faz 3 — Sonuç kartları (Hotel/Flight)
- **Şablon hazır:** login sol panelindeki uçuş kartı (IST→CDG rota çizgisi,
  uçak ikonu) ve otel kartı birebir bu iş için tasarlanmış — `FlightCard` ve
  `HotelCard`'ı bu dile taşı.
- Hover: hafif yükselme + border teal'e dönme; fiyat `NumberFlow` ile.
- Liste sayfaları (Hotels/Flights): koyu zemin, filtre paneli cam kart,
  skeleton shimmer yükleme durumu.

### Faz 4 — Kontrollü bölge kontrastı
- Rezervasyon formu açık temada kalır ama cilalanır: adım göstergesi (stepper),
  net tipografi, `react-day-picker` tarihler.
- Chat'ten forma geçişte tam sayfa geçiş animasyonu — "AI bölgesinden çıkış"
  anını görselleştir (ör. koyu→açık fade). Jüri anlatısının kilit anı.
- Onay ekranı: sonner toast + sakin başarı durumu.

### Faz 5 — Cila (jüri farkı burada)
- Boş durumlar (sonuç yok, ilk mesaj öncesi karşılama hero'su).
- Hata durumları, klavye odak halkaları (teal ring), `prefers-reduced-motion`.
- Favicon + sekme başlığı, responsive tur (mobil chat!), Lighthouse hızlı geçiş.

## 6. İterasyon döngüsü (her bileşen için)

1. Magic `component_inspiration` ile varyantlara bak → yön seç.
2. Magic/shadcn ile üret — istekte sistemi sabitle:
   *"…üret: cam kart reçetesi, sadece brand-* renkleri, Inter, gap-4"*.
3. `npm run dev` → bileşeni `Design.tsx` drop-zone'una koy.
4. Playwright MCP ile ekran görüntüsü → kritik → düzelt (2-3 tur).
5. Onaylanan bileşeni feature klasörüne taşı, test + commit.

## 7. Yapılmayacaklar

- Her ekranı koyu yapmak (rezervasyon kontrastı konseptin parçası).
- Ham hex renk, sistem dışı font, rastgele radius.
- Cursor-trail, particles.js, 3D globe gibi performansı yiyen süsler —
  blob alanı zaten var, ikinci bir "efekt katmanı" ekleme.
- Fiyat/veri uydurma: kartlardaki tüm veriler MSW fixture'larından gelir.
