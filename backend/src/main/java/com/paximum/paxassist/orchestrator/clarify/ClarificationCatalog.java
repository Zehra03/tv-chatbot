package com.paximum.paxassist.orchestrator.clarify;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns a business module's technical missing-field names into a friendly Turkish clarifying
 * question. The field-name strings mirror exactly what the modules report:
 * hotel → {@code destination, checkIn, night, adult}; flight → {@code origin, destination,
 * departDate, tripType, returnDate, passengers, currency}.
 *
 * <p>Design choice: we ask for the FIRST missing field only (one short question per turn),
 * which matches the project's conversational slot-filling style rather than dumping every gap
 * at once. Deterministic and fully unit-testable — no LLM needed for the wording (LLM phrasing
 * is a later, optional enhancement gated by the evaluator-optimizer).
 */
@Component
public class ClarificationCatalog {

    private static final Map<String, String> HOTEL_QUESTIONS = Map.of(
            "destination", "Hangi şehir veya bölgede otel aramamı istersiniz?",
            "checkIn", "Otele giriş tarihiniz nedir? (örn. 2026-08-01)",
            "night", "Kaç gece konaklamayı planlıyorsunuz? (ya da çıkış tarihinizi belirtin)",
            "adult", "Kaç yetişkin konaklayacak?"
    );

    private static final Map<String, String> FLIGHT_QUESTIONS = Map.of(
            "origin", "Nereden kalkmak istersiniz?",
            "destination", "Nereye gitmek istersiniz?",
            "departDate", "Hangi tarihte gitmek istersiniz? (örn. 2026-08-01)",
            "returnDate", "Dönüş tarihiniz nedir?",
            "tripType", "Tek yön mü yoksa gidiş-dönüş mü olsun?",
            "passengers", "Kaç yolcu seyahat edecek?",
            "currency", "Fiyatları hangi para biriminde görmek istersiniz? (örn. TRY)"
    );

    private static final String HOTEL_FALLBACK = "Otel araması için biraz daha bilgiye ihtiyacım var. Yardımcı olabilir misiniz?";
    private static final String FLIGHT_FALLBACK = "Uçuş araması için biraz daha bilgiye ihtiyacım var. Yardımcı olabilir misiniz?";

    public String questionForHotel(List<String> missingFields) {
        return firstQuestion(HOTEL_QUESTIONS, missingFields, HOTEL_FALLBACK);
    }

    public String questionForFlight(List<String> missingFields) {
        return firstQuestion(FLIGHT_QUESTIONS, missingFields, FLIGHT_FALLBACK);
    }

    private String firstQuestion(Map<String, String> catalog, List<String> missingFields, String fallback) {
        if (missingFields == null || missingFields.isEmpty()) {
            return fallback;
        }
        String first = missingFields.get(0);
        return catalog.getOrDefault(first, fallback);
    }
}
