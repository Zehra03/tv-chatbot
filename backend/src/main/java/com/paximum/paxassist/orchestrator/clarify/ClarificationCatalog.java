package com.paximum.paxassist.orchestrator.clarify;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns a business module's technical missing-field names into a friendly Turkish clarifying
 * question. The field-name strings mirror exactly what the modules report:
 * hotel → {@code destination, checkIn, night, adult, childAges}; flight → {@code origin, destination,
 * departDate, tripType, returnDate, passengers}.
 *
 * <p>Currency is deliberately absent: it is never asked for, it follows from where the request came
 * from ({@link com.paximum.paxassist.orchestrator.mapper.CurrencyByCountry}) and falls back to TRY,
 * so the chat mappers always fill it and no turn can report it missing.</p>
 *
 * <p>Design choice: we ask for the FIRST missing field only (one short question per turn),
 * which matches the project's conversational slot-filling style rather than dumping every gap
 * at once. Deterministic and fully unit-testable — no LLM needed for the wording (LLM phrasing
 * is a later, optional enhancement gated by the evaluator-optimizer).
 */
@Component
public class ClarificationCatalog {

    private static final Map<String, String> HOTEL_QUESTIONS = Map.of(
            "destination", "Hangi şehir veya bölgede otel aramamı istersin?",
            "checkIn", "Otele giriş tarihin nedir? (örn. 2026-08-01)",
            "night", "Kaç gece konaklamayı planlıyorsun? (ya da çıkış tarihini belirt)",
            "adult", "Kaç yetişkin konaklayacak?",
            "childAges", "Çocukların yaşları nedir? (örn. 5 ve 8) — fiyat çocuk yaşına göre değişir."
    );

    private static final Map<String, String> FLIGHT_QUESTIONS = Map.of(
            "origin", "Nereden kalkmak istersin?",
            "destination", "Nereye gitmek istersin?",
            "departDate", "Hangi tarihte gitmek istersin? (örn. 2026-08-01)",
            // Asked when the user said "gidiş-dönüş" without a return date — name the trip type back
            // so the question does not read as a random second date request.
            "returnDate", "Gidiş-dönüş için planlıyorum. Dönüş tarihin hangi gün olsun? (örn. 2026-08-08)",
            "tripType", "Tek yön mü yoksa gidiş-dönüş mü olsun?",
            "passengers", "Kaç yolcu seyahat edecek?",
            // Not a field the flight module reports: FlightSearchHandler asks for it, because the
            // fare depends on whether a child flies as an infant (0-1), a child (2-11) or pays the
            // adult fare (12+).
            "childAges", "Çocukların yaşları nedir? (örn. 1 ve 8) — 2 yaş altı bebek, 12 yaş ve üzeri "
                    + "yetişkin ücretine tabi olduğu için fiyat değişir."
    );

    private static final String HOTEL_FALLBACK = "Otel araması için biraz daha bilgiye ihtiyacım var. Yardımcı olabilir misin?";
    private static final String FLIGHT_FALLBACK = "Uçuş araması için biraz daha bilgiye ihtiyacım var. Yardımcı olabilir misin?";

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
