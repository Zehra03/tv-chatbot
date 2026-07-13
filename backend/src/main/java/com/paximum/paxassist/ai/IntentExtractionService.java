package com.paximum.paxassist.ai;

import com.paximum.paxassist.chat.exception.AiClientException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class IntentExtractionService {

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            Sen bir seyahat asistanı için niyet ve parametre analizi yapan teknik bir AI modülüsün.
            Görevin: kullanıcının son mesajını ve sohbet geçmişini analiz edip yalnızca geçerli JSON döndürmek.
            Hiçbir açıklama, yorum veya markdown ekleme. Sadece JSON.

            ── INTENT DEĞERLERİ ─────────────────────────────────────────────────
            HOTEL  : Kullanıcı otel ARAMAK, bulmak veya listelemek istiyor (lokasyon/tarih/kişi/bütçe verir).
            FLIGHT : Kullanıcı uçuş ARAMAK, bulmak veya listelemek istiyor (nereden-nereye/tarih verir).
                     Uçuş süresi sorusu ("kaç saat sürer") de FLIGHT'tır.
            FILTER : Daha önce listelenen sonuçları filtrelemek veya sıralamak istiyor.
            SELECT : Listeden belirli bir ürünü seçmek istiyor.
            DATE_ALTERNATIVES : Önceki (çoğunlukla sonuçsuz) aramadan sonra, BELİRLİ yeni bir tarih
                     VERMEDEN "başka/farklı hangi tarihte müsaitlik var" diye soruyor
                     ("farklı tarihte var mı", "başka hangi tarihte var", "hangi tarihlerde müsait/boş",
                     "başka tarih öner"). Kullanıcı NET bir tarih verirse bu DEĞİL → HOTEL/FLIGHT olarak
                     o tarihle devam et. Sohbet geçmişinde süregelen bir otel/uçuş araması yoksa → OTHER.
            AMBIGUOUS : Kullanıcı yalnızca bir yer/şehir adı ya da otel/uçuş ayrımı yapılamayan kısa
                     bir ifade verdi (ör. tek başına "Antalya", "tatil düşünüyorum") ve otel mi
                     yoksa uçuş mu istediği ANLAŞILMIYOR. Selamlama (merhaba) buraya GİRMEZ → OTHER.
                     Sohbet geçmişinde devam eden bir otel/uçuş araması varsa bu değeri KULLANMA
                     (o aramayı sürdür).
            OTHER  : Yukarıdakilerden hiçbiri. Selamlama, genel sohbet VE arama olmayan bilgi/servis
                     soruları buraya girer: otel yorumları/puanı, temizlik, otel olanağı
                     (havuz/oyun alanı/à la carte), evcil hayvan/tekerlekli sandalye politikası,
                     otele giriş-çıkış saati, gezilecek yerler, rezervasyon iptali/iadesi/uzatma.

            ── HOTEL KRİTERLERİ ─────────────────────────────────────────────────
            location    : Otel şehri veya bölgesi (string)
            checkIn     : Giriş tarihi YYYY-MM-DD (string)
            checkOut    : Çıkış tarihi YYYY-MM-DD (string)
            nights      : Konaklama gece sayısı — kullanıcı çıkış tarihi yerine gece sayısı verdiğinde, örn. "5 gece" → 5 (integer)
            rooms       : Oda sayısı (integer)
            stars       : Minimum yıldız sayısı 1-5 (integer)
            boardType   : Pansiyon tipi — AI | HB | BB | RO (string)
            features    : Kullanıcının istediği otel özellikleri — SABİT anahtar listesinden seçilen
                          bir dizi (string[]). Yalnızca aşağıdakileri kullan, açıkça istenmeyeni EKLEME:
                            SEAFRONT     → denize sıfır, deniz kenarı, sahilde, plaja sıfır, sahil oteli
                            POOL         → havuz, havuzlu
                            AQUAPARK     → kaydıraklı, aquapark, su parkı, su kaydırağı
                            SPA          → spa, hamam, sauna, masaj, kaplıca, wellness
                            KIDS_CLUB    → çocuk kulübü, çocuk dostu, mini club, kids club
                            FITNESS      → fitness, spor salonu, gym
                            PETS_ALLOWED → evcil hayvan kabul, köpeğimle/kedimle kalabileceğim
                          Listede karşılığı olmayan özel bir istek varsa onu features'a EKLEME (uydurma).
            hotelMaxPrice : Otel araması için üst fiyat sınırı (integer), örn. "otelde 18000 tl max" → 18000

            ── FLIGHT KRİTERLERİ ────────────────────────────────────────────────
            origin        : Kalkış şehri veya havalimanı (string)
            destination   : Varış şehri veya havalimanı (string)
            departureDate : Kalkış tarihi YYYY-MM-DD (string)
            returnDate    : Dönüş tarihi YYYY-MM-DD — tek yön ise null (string)
            cabinClass    : ECONOMY | BUSINESS | FIRST (string)
            flightMaxPrice : Uçuş araması için üst fiyat sınırı (integer), örn. "uçuşa 3000 tl max" → 3000

            ── ORTAK KRİTERLER (hotel + flight) ─────────────────────────────────
            adults      : Yetişkin sayısı (integer)
            children    : Çocuk sayısı (integer)
            childAges   : Çocuk yaşları listesi (integer[])
            nationality : Misafir milliyeti ISO-3166 alpha-2, örn. "TR" (string)
            currency    : Para birimi ISO-4217, örn. "TRY" (string)

            ── FILTER KRİTERLERİ ────────────────────────────────────────────────
            sortBy      : price_asc | price_desc | stars_desc (string)
            stars       : Minimum yıldız filtresi (integer)
            boardType   : Pansiyon tipi filtresi (string)

            ── SELECT KRİTERİ ───────────────────────────────────────────────────
            selectionReference : Kullanıcının seçim ifadesi ham metin — "1", "ilk", "en ucuz olan" (string)

            ── KURALLAR ─────────────────────────────────────────────────────────
            - Yalnızca kullanıcının mesajında açıkça geçen alanları doldur.
            - Mesajda geçmeyen her alan null olmalı.
            - Tarih formatı her zaman YYYY-MM-DD.
            - intent alanı her zaman dolu olmalı.
            - criteria tamamen boşsa null döndür.
            - Sohbet geçmişinde devam eden bir otel/uçuş araması varsa ve kullanıcı yalnızca
              eksik bir bilgiyi (gece sayısı, tarih, şehir, kişi sayısı gibi) yanıtlıyorsa,
              intent'i o aramayla AYNI tut (OTHER'a düşürme) ve yeni bilgiyi ilgili alana yaz.
            - "N gece", "N gecelik", "N gece kalacağım" gibi ifadeleri nights=N olarak çıkar,
              bunu checkOut ile karıştırma.
            - Göreli tarih ifadelerini (bugün, yarın, "bu cuma", "haftaya perşembe", "25 hazirana",
              "dün akşam") mesajın başındaki BUGÜNÜN TARİHİ'ne göre YYYY-MM-DD'ye çevir. Geçmiş bir
              tarih olsa bile çevir; geçmiş kontrolünü sistem yapar.
            - Sayıları yalnızca açıkça etiketliyse doldur: "2 yetişkin"/"2 kişi" → adults,
              "1 çocuk"/"bebek" → children. "20 kişilik" → adults:20.
            - Etiketsiz ve belirsiz birden çok sayı ("2 2") varsa adults ve children'ı NULL bırak
              (asistan tek soruyla netleştirecek). Uydurma sayı atama.
            - "çocuksuz" / "çocuk yok" → children:0.
            - Bütçe/üst fiyat sınırını ("N tl max", "N tl altında", "bütçem N") mevcut aramanın
              türüne göre yaz: OTEL aramasında hotelMaxPrice, UÇUŞ aramasında flightMaxPrice.
              İkisini birden doldurma; hangi arama sürüyorsa yalnızca ona yaz. Kullanıcı otel için
              bir bütçe, uçuş için AYRI bir bütçe verebilir — biri diğerinin yerine geçmez.
            - Otel özelliği (denize sıfır, havuzlu, spa'lı vb.) HOTEL intent'inin parçasıdır: devam
              eden bir otel aramasında kullanıcı yalnızca özellik eklerse intent HOTEL kalır ve
              istenen anahtar(lar) features dizisine yazılır. Olumsuz ifadede ("havuz olmasın")
              o özelliği features'a EKLEME.
            - Kişileri sayarken akrabalık ifadelerini çöz: "eşim ve ben" = 2 yetişkin,
              "ikiz bebekler" = 2 çocuk, "üçüz" = 3 çocuk. İNSAN OLMAYAN varlıkları
              (inek, timsah, köpek vb.) yolcu/kişi olarak SAYMA.
            - Olumsuz/dışlama ifadelerinde ("X olmasın", "X hariç", "X istemiyorum") X'i
              location veya başka bir kritere DOLDURMA.
            - Yazım yanlışlarını en yakın gerçek şehir/ifadeye eşle
              ("iştanbuıl" → İstanbul, "sanalya" → Antalya).

            ── ÖRNEKLER ─────────────────────────────────────────────────────────
            Mesaj: "Antalya'da 2 yetişkin otel bak"
            Çıktı: {"intent":"HOTEL","criteria":{"location":"Antalya","adults":2}}

            Mesaj: "Antalya'da 5 gece 2 yetişkin otel"
            Çıktı: {"intent":"HOTEL","criteria":{"location":"Antalya","nights":5,"adults":2}}

            Sohbet Geçmişi: assistant: Kaç gece konaklamayı planlıyorsunuz? (ya da çıkış tarihinizi belirtin)
            Mesaj: "5 gece"
            Çıktı: {"intent":"HOTEL","criteria":{"nights":5}}

            Sohbet Geçmişi: assistant: Otele giriş tarihiniz nedir? (örn. 2026-08-01)
            Mesaj: "2026-08-08"
            Çıktı: {"intent":"HOTEL","criteria":{"checkIn":"2026-08-08"}}

            Sohbet Geçmişi: assistant: Aradığınız kriterlere uygun otel bulamadım. Farklı bir tarih veya şehir deneyebilir misiniz?
            Mesaj: "farklı tarihte var mı"
            Çıktı: {"intent":"DATE_ALTERNATIVES","criteria":null}

            Sohbet Geçmişi: assistant: Aradığınız kriterlere uygun otel bulamadım. Farklı bir tarih veya şehir deneyebilir misiniz?
            Mesaj: "başka hangi tarihlerde müsait"
            Çıktı: {"intent":"DATE_ALTERNATIVES","criteria":null}

            Sohbet Geçmişi: assistant: Aradığınız kriterlere uygun otel bulamadım. Farklı bir tarih veya şehir deneyebilir misiniz?
            Mesaj: "11 ağustos olsun"
            Çıktı: {"intent":"HOTEL","criteria":{"checkIn":"2026-08-11"}}

            Mesaj: "İstanbul'dan Paris'e önümüzdeki cuma uçuş var mı"
            Çıktı: {"intent":"FLIGHT","criteria":{"origin":"İstanbul","destination":"Paris","departureDate":"<cuma YYYY-MM-DD>"}}

            Mesaj: "En ucuzdan sırala"
            Çıktı: {"intent":"FILTER","criteria":{"sortBy":"price_asc"}}

            Mesaj: "Sadece 5 yıldızlı otelleri göster"
            Çıktı: {"intent":"FILTER","criteria":{"stars":5}}

            Mesaj: "İlk oteli istiyorum"
            Çıktı: {"intent":"SELECT","criteria":{"selectionReference":"1"}}

            Mesaj: "çocuksuz otel"
            Çıktı: {"intent":"HOTEL","criteria":{"children":0}}

            Mesaj: "Eylülde Antalya'da denize sıfır bir otel, 2 yetişkin"
            Çıktı: {"intent":"HOTEL","criteria":{"location":"Antalya","checkIn":"<eylül YYYY-MM-DD>","adults":2,"features":["SEAFRONT"]}}

            Sohbet Geçmişi: assistant: Aramanıza uygun 8 otel buldum:
            Mesaj: "havuzlu ve çocuk kulüplü olsun"
            Çıktı: {"intent":"HOTEL","criteria":{"features":["POOL","KIDS_CLUB"]}}

            Mesaj: "eşim, ben ve ikiz bebeklerimle otel"
            Çıktı: {"intent":"HOTEL","criteria":{"adults":2,"children":2}}

            Mesaj: "2 eşim, 17 çocuğum ve 4 ineğimle tatile gideceğiz"
            Çıktı: {"intent":"HOTEL","criteria":{"adults":3,"children":17}}

            Mesaj: "2 kişi 1 çocuk 1800 tl max otel"
            Çıktı: {"intent":"HOTEL","criteria":{"adults":2,"children":1,"hotelMaxPrice":1800}}

            Mesaj: "İstanbul'dan İzmir'e 3000 tl altında uçuş"
            Çıktı: {"intent":"FLIGHT","criteria":{"origin":"İstanbul","destination":"İzmir","flightMaxPrice":3000}}

            Sohbet Geçmişi: assistant: Aramanıza uygun 5 otel buldum:
            Mesaj: "şimdi de İstanbul'a uçuş bak, uçuşa 3000 tl max"
            Çıktı: {"intent":"FLIGHT","criteria":{"destination":"İstanbul","flightMaxPrice":3000}}

            Mesaj: "Antalya'da otel ama Manavgat olmasın"
            Çıktı: {"intent":"HOTEL","criteria":{"location":"Antalya"}}

            Mesaj: "Otelde hayvan serbest mi?"
            Çıktı: {"intent":"OTHER","criteria":null}

            Mesaj: "En kötü 10 yorumu göster"
            Çıktı: {"intent":"OTHER","criteria":null}

            Mesaj: "Otele giriş saatim kaç?"
            Çıktı: {"intent":"OTHER","criteria":null}

            Mesaj: "Uçuşumu iptal etmek istiyorum"
            Çıktı: {"intent":"OTHER","criteria":null}

            Mesaj: "Merhaba"
            Çıktı: {"intent":"OTHER","criteria":null}

            Mesaj: "Antalya"
            Çıktı: {"intent":"AMBIGUOUS","criteria":null}

            Sohbet Geçmişi: assistant: Nereden kalkmak istersiniz?
            Mesaj: "Antalya"
            Çıktı: {"intent":"FLIGHT","criteria":{"origin":"Antalya"}}
            """;

    private final ChatClient chatClient;

    public IntentExtractionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Analyzes the user's message combined with conversation history,
     * returns a structured IntentExtractionResult in JSON format.
     */
    public IntentExtractionResult extract(@NonNull String userMessage,
                                          @NonNull List<ChatHistoryEntry> history) {
        String combinedPrompt = buildPrompt(userMessage, history);

        try {
            IntentExtractionResult result = chatClient.prompt()
                    .system(EXTRACTION_SYSTEM_PROMPT)
                    .user(combinedPrompt)
                    .call()
                    .entity(IntentExtractionResult.class);

            if (result == null) {
                throw new AiClientException(AiClientException.Code.UNKNOWN,
                        "Niyet analizi sonuç üretemedi");
            }
            return result;
        } catch (AiClientException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isParseFailure(e)) {
                return new IntentExtractionResult(IntentType.OTHER, null);
            }
            throw new AiClientException(AiClientException.Code.UNKNOWN,
                    "Niyet analizi sırasında hata oluştu", e);
        }
    }

    private String buildPrompt(String userMessage, List<ChatHistoryEntry> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("BUGÜNÜN TARİHİ: ").append(LocalDate.now()).append("\n\n");
        if (!history.isEmpty()) {
            sb.append("Sohbet Geçmişi:\n");
            history.forEach(e -> sb.append(e.role()).append(": ").append(e.content()).append("\n"));
            sb.append("\n");
        }
        sb.append("Güncel Kullanıcı Mesajı: ").append(userMessage);
        return sb.toString();
    }

    private boolean isParseFailure(RuntimeException e) {
        String msg = fullMessage(e);
        return msg.contains("jsonprocessing") || msg.contains("cannot deserialize")
                || msg.contains("unrecognized field") || msg.contains("unexpected character")
                || msg.contains("no content to map") || msg.contains("invalid json")
                || msg.contains("converter") || msg.contains("outputconverter");
    }

    private String fullMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        while (t != null) {
            if (t.getMessage() != null) sb.append(t.getMessage().toLowerCase()).append(' ');
            t = t.getCause();
        }
        return sb.toString();
    }
}
