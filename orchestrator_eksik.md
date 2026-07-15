# Handoff — Berat'a: Domain Bazlı Kart Ayrıştırma

**Osman · 2026-07-15 · `ai-intention-improvements-v2`**

`chat/` tarafı bitti ve çalıştırılarak doğrulandı: kartlar artık `ChatSession` içinde domain başına ayrı kutularda ve restart'tan sağ çıkıyorlar (`V7__chat_session_active_domain.sql` ile `active_domain` persist ediliyor). Mevcut API imzaları değişmedi — `getLastResultCards()`/`setLastResultCards()` artık aktif domain'in kutusuna yönleniyor, ek olarak `getResultCards("HOTEL")`/`setResultCards("HOTEL", cards)` var. `orchestrator/` altında tek satır değiştirmedim; kalan iki iş orada.

**İş 1 (P0, ~4 satır).** `activeDomain` şu an sadece arama **başarıyla tamamlandığında** yazılıyor. Kullanıcı "şimdi uçuş bak" deyip clarify'a girdiğinde domain hâlâ `HOTEL` kalıyor ve o aralıkta gelen FILTER/SELECT bayat otel kartlarını okuyor — canlıda doğruladım: uçuş clarify'ının ortasında "en ucuzdan sırala" dedim, 32 tane `productType: hotel` kartı döndü. Çözüm, `ChatOrchestrationService.handle()` içinde extraction'dan sonra (satır ~69) ve routing'den önce (satır ~83). FILTER/SELECT `activeDomain`'i **değiştirmemeli**:

```java
// Domain'i niyet tespit edilir edilmez güncelle; aramanın bitmesini bekleme.
if (extraction.intent() == IntentType.HOTEL) {
    session.setActiveDomain("HOTEL");
} else if (extraction.intent() == IntentType.FLIGHT) {
    session.setActiveDomain("FLIGHT");
}
```

Bunu ekleyince `FilterHandler:31` zaten doğru cevabı veriyor ("Önce bir otel veya uçuş araması yapmalıyız"). Bedava gelen ikinci düzelme: `JpaChatSessionStore:163-169` her turda son assistant mesajına aktif kutunun kartlarını iliştirdiği için şu an uçuş clarify mesajının yanına 32 otel kartı kaydediliyor (DB'de doğruladım) — domain doğru anda değişince o da kapanıyor.

**İş 2.** `result_cards` jsonb olduğu için DB'den dönen kartlar `LinkedHashMap`, `HotelProduct`/`FlightProduct` değil. `ProductCards.priceOf()` sadece somut tipleri tanıdığından hepsini `null` okuyor → `nullsLast` → sıralama no-op, ama kullanıcıya "İşte güncellenmiş sıralama:" deniyor: hata değil, **sessizce yanlış cevap**. Canlıda: liste azalan sıradayken "en ucuzdan sırala" → hiç değişmedi. `FilterHandler`, `SelectHandler:90` ve `ResultFilters:37` (yani develop'taki `limit` işi de) etkileniyor; `liveCache`'in eviction'ı olmadığı için **her deploy'da** tüm oturumlar bu duruma düşüyor, çok replikalı çalışmada ise sürekli. Çözüm: `ProductCards`'a map fallback'i ekle (`map.get("price")` / `map.get("stars")`) — `ChatViewMapper`/`ResultCardDomain` aynı deseni zaten kullanıyor. Alternatif olarak restore sırasında map'leri tipli nesneye geri çevirebilirim (o `chat/` tarafında kalır); tüketici sen olduğun için kararı sana bırakıyorum. İş 2 olmadan restore düzeltmem anlamsız: kartları doğru kutuya koyuyorum ama sıralanamıyorlar.

**Kabul kriteri:** otel araması → "şimdi uçuş bak" → clarify sürerken "en ucuzdan sırala" → eski otel kartları dönmez; otel→uçuş→"otele dönelim" → otel kartları hâlâ orada (restart sonrası dahil — bu ayak hazır); restart sonrası FILTER **gerçekten sıralar**. Testler bende değil (`test/` scope dışım): `ChatSessionTest` (kutu ayrımı, legacy `active_domain == null` davranışı), `JpaChatSessionStoreTest` (iki domain'li oturum restore edilince **her iki kutu da dolu** gelmeli) ve İş 1/İş 2 için orchestrator testleri lazım. Migration notu: bu dosya önce `V6` idi, berfdeniz'in `V6__passenger_type_infant`'ı ile çakıştığı için `V7`'ye kaydırdım — eski halini çalıştırıp V6'yı DB'sine uygulamış biri varsa Flyway `Detected applied migration not resolved locally: 6` ile açılmayı reddeder, `flyway repair` gerekir.
