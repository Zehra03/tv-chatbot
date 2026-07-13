# AI Intention Improvements — Özet

Branch: `feature/backend/ai-intention-improvements`
Kapsam: `ai/` modülü (Osman). Orchestrator dokunuşları modül sahibiyle koordine edildi.

---

## Madde 1 — Domain'e özel bütçe (maxPrice bölme) ✅ commit'li

**Sorun:** Otel ve uçuş tek bir `maxPrice` alanını paylaşıyordu. Kullanıcı otelde "18000 tl max"
deyince o değer aynı session'daki uçuş aramasına da uygulanıyordu.

**İnceleme sonucu (önemli):** Tarih ve lokasyon aslında **karışmıyor** — otel (`location`,
`checkIn`) ve uçuş (`origin`/`destination`, `departureDate`) ayrı alanlarda ve her mapper yalnızca
kendi alanlarını okuyor. Paylaşılan alanlar (`adults`, `children`, `currency`, `nationality`)
zaten ortak olmalı. **Tek yanlış paylaşılan alan `maxPrice`'tı.**

**Çözüm:**
- `ai/SlotCriteria`: `maxPrice` → `hotelMaxPrice` + `flightMaxPrice` (domain'e özel).
- Extraction prompt: "bütçe otel niyetindeyse hotelMaxPrice, uçuş niyetindeyse flightMaxPrice;
  ikisi ayrı, biri diğerini ezmez" kuralı + örnekler.
- `orchestrator/SlotMerger`: iki bütçeyi ayrı ayrı merge ediyor.
- `orchestrator/HotelSearchHandler` → `hotelMaxPrice`, `FlightSearchHandler` → `flightMaxPrice`.
- Testler güncellendi: SlotMergerTest (8/8), HotelSearchHandlerTest (8/8), FlightSearchHandlerTest (4/4).

**Commit'ler:** `48fc373` (ai), `253e338` (orchestrator), `07fdbad` + `4c6cbc8` (testler).

---

## Madde 2 — Intent domain-switch + prompt sağlamlaştırma ⚠️ commit bekliyor

**Sorun:** Otel araması sürerken kullanıcı uçuşa geçtiğinde, LLM "devam eden aramayı sürdür"
(stickiness) eğilimi yüzünden mesajı hâlâ HOTEL sınıflayabiliyordu → uçuş şehri yanlışlıkla otel
`location`'ına yazılıyordu.

**Netleştirme:** Saklanan ve "güncellenmeyi unutulan" bir intent yok — her mesajda sıfırdan
sınıflanıyor. Otel şehrinin uçuşu etkilemesinin **tek yolu** intent'in yanlış sınıflanması.
Slot torbasındaki eski değer (ayrı alan olduğu için) zararsız.

**Çözüm (tamamı `ai/` prompt):**
- **DOMAIN SWITCH kuralı**: kullanıcı açıkça ("uçuş bak", "boşver uçuşa geçelim") veya örtük olarak
  ("orada kalacağım" → HOTEL, "oraya gideceğim" → FLIGHT) domain değiştirdiğinde stickiness'i
  ezip yeni intent'e geçiyor. Yeni domain'in şehri doğru alana (origin/destination vs location) yazılıyor.
- **Prompt yeniden yapılandırma**: XML bölümleri (`<context>`, `<persona>`, `<security>`,
  `<intents>`, `<schema>`, `<rules>`, `<output_format>`, `<examples>`). Kural/şema İngilizce,
  few-shot örnekler Türkçe.
- **`<context>` koda bağlandı**: `renderSystemPrompt()` her istekte `{{CURRENT_DATE}}` ve
  `{{CURRENT_WEEKDAY}}`'i gerçek değerlerle dolduruyor (relative-date için gün adı da enjekte
  ediliyor). `buildPrompt`'taki çift-kaynak Türkçe tarih satırı kaldırıldı.
- **`<security>`**: prompt injection'ı veri olarak işleyip OTHER sınıflama; injection→OTHER kuralı
  tek yerde (iç tekrar temizlendi).
- **Sayısal tarih disambiguasyonu**: D/M/YYYY vb.; biri >12 ise gün, ikisi de ≤12 ise belirsiz → null.
- **Raw değer** kuralı: negatif/0 sayıları düzeltmeden çıkar (validasyon downstream).
- **checkIn:null tutarlılığı**: belirsiz tarih artık null yazılmıyor, alan atlanıyor.

**Durum:** Çalışma ağacında hazır, derleniyor. Henüz **commit edilmedi** ve **canlı LLM ile
doğrulanmadı** (API key gerekiyor — domain-switch ve weekday enjeksiyonunun gerçekten çalıştığı
canlıda test edilmeli).

---

## Madde 3 — Kısmi düzeltme ✅ doğrulandı, değişiklik yok

"Aslında 3 kişi olacaktı" dendiğinde extraction sadece `{adults:3}` döndürür ve `SlotMerger.pick`
yalnızca `adults`'ı günceller; `location`/`checkIn` korunur. Mekanizma zaten doğru — düzeltme gerekmedi.

---

## Açık işler / notlar

- **Push:** maxPrice commit'leri push bekliyor (geçici sistem sorunu yüzünden gönderilemedi).
- **Downstream doğrulama:** negatif/0 yolcu sayısını hotel/flight modülünün reddettiği teyit
  edilmeli (orchestrator/hotel/flight — Osman kapsamı dışı, modül sahibine).
- **Canlı doğrulama:** madde 2 prompt değişiklikleri gerçek AI sağlayıcıyla test edilmeli.
