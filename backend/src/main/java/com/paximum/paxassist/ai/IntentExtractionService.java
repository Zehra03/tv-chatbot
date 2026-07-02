package com.paximum.paxassist.ai;

import com.paximum.paxassist.chat.exception.AiClientException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IntentExtractionService {

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            Sen bir seyahat asistanı için niyet ve parametre analizi yapan teknik bir AI modülüsün.
            Görevin: kullanıcının son mesajını ve sohbet geçmişini analiz edip yalnızca geçerli JSON döndürmek.
            Hiçbir açıklama, yorum veya markdown ekleme. Sadece JSON.

            ── INTENT DEĞERLERİ ─────────────────────────────────────────────────
            HOTEL  : Kullanıcı otel arıyor veya otel hakkında soru soruyor.
            FLIGHT : Kullanıcı uçuş arıyor veya uçuş hakkında soru soruyor.
            FILTER : Daha önce listelenen sonuçları filtrelemek veya sıralamak istiyor.
            SELECT : Listeden belirli bir ürünü seçmek istiyor.
            OTHER  : Yukarıdakilerden hiçbiri (selamlama, genel soru, kapsam dışı).

            ── HOTEL KRİTERLERİ ─────────────────────────────────────────────────
            location    : Otel şehri veya bölgesi (string)
            checkIn     : Giriş tarihi YYYY-MM-DD (string)
            checkOut    : Çıkış tarihi YYYY-MM-DD (string)
            rooms       : Oda sayısı (integer)
            stars       : Minimum yıldız sayısı 1-5 (integer)
            boardType   : Pansiyon tipi — AI | HB | BB | RO (string)

            ── FLIGHT KRİTERLERİ ────────────────────────────────────────────────
            origin        : Kalkış şehri veya havalimanı (string)
            destination   : Varış şehri veya havalimanı (string)
            departureDate : Kalkış tarihi YYYY-MM-DD (string)
            returnDate    : Dönüş tarihi YYYY-MM-DD — tek yön ise null (string)
            cabinClass    : ECONOMY | BUSINESS | FIRST (string)

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

            ── ÖRNEKLER ─────────────────────────────────────────────────────────
            Mesaj: "Antalya'da 2 yetişkin otel bak"
            Çıktı: {"intent":"HOTEL","criteria":{"location":"Antalya","adults":2}}

            Mesaj: "İstanbul'dan Paris'e önümüzdeki cuma uçuş var mı"
            Çıktı: {"intent":"FLIGHT","criteria":{"origin":"İstanbul","destination":"Paris","departureDate":"<cuma YYYY-MM-DD>"}}

            Mesaj: "En ucuzdan sırala"
            Çıktı: {"intent":"FILTER","criteria":{"sortBy":"price_asc"}}

            Mesaj: "Sadece 5 yıldızlı otelleri göster"
            Çıktı: {"intent":"FILTER","criteria":{"stars":5}}

            Mesaj: "İlk oteli istiyorum"
            Çıktı: {"intent":"SELECT","criteria":{"selectionReference":"1"}}

            Mesaj: "Merhaba"
            Çıktı: {"intent":"OTHER","criteria":null}
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
                    .options(OllamaOptions.builder().numPredict(512).build())
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
