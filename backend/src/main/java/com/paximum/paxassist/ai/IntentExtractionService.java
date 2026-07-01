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
            Sen bir seyahat chatbotu için niyet ve parametre analizi yapan teknik bir AI modülüsün.
            Kullanıcının son mesajını ve sohbet geçmişini analiz et.
            Yalnızca geçerli JSON döndür, hiçbir ek açıklama veya markdown ekleme.

            intent değerleri (biri zorunlu):
            - HOTEL  : kullanıcı otel arıyor, otel hakkında soru soruyor
            - FLIGHT : kullanıcı uçuş arıyor, uçuş hakkında soru soruyor
            - FILTER : daha önce listelenen sonuçları filtrelemek istiyor (daha ucuz, 5 yıldız vb.)
            - SELECT : listeden belirli bir ürünü seçmek istiyor ("1. oteli istiyorum", "ilkini al" vb.)
            - OTHER  : yukarıdakilerden hiçbiri (selamlama, genel soru vb.)

            criteria alanları (yalnızca mesajda geçen bilgileri doldur, gerisini null bırak):
            - location    : otel konumu veya şehir (string)
            - checkIn     : giriş tarihi (YYYY-MM-DD)
            - checkOut    : çıkış tarihi (YYYY-MM-DD)
            - adults      : yetişkin sayısı (integer)
            - children    : çocuk sayısı (integer)
            - childAges   : çocuk yaşları listesi (integer[])
            - nationality : misafir milliyeti, ISO-3166 alpha-2 (string)
            - currency    : para birimi, ISO-4217 (string)
            - rooms       : oda sayısı (integer)
            - stars       : minimum yıldız sayısı (integer, 1-5)
            - boardType   : pansiyon tipi (AI/HB/BB/RO) (string)
            - sortBy      : sıralama tercihi: price_asc | price_desc | stars_desc (string)
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
            throw new AiClientException(AiClientException.Code.UNKNOWN,
                    "Niyet analizi sırasında hata oluştu", e);
        }
    }

    private String buildPrompt(String userMessage, List<ChatHistoryEntry> history) {
        StringBuilder sb = new StringBuilder();

        if (!history.isEmpty()) {
            sb.append("Sohbet Geçmişi:\n");
            history.forEach(entry ->
                    sb.append(entry.role()).append(": ").append(entry.content()).append("\n")
            );
            sb.append("\n");
        }

        sb.append("Güncel Kullanıcı Mesajı: ").append(userMessage);
        return sb.toString();
    }
}
