package com.paximum.paxassist.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Wire-contract for the multi-language fields the intent model now emits: the JSON it is prompted to
 * produce must survive into {@link IntentExtractionResult#detectedLanguage()} /
 * {@link IntentExtractionResult#languageConfidence()}. The mapper mirrors how Spring AI's
 * BeanOutputConverter reads records (canonical constructor + parameter names), so a green test means
 * the model's language output reaches the backend rather than being dropped.
 *
 * <p>Absence of the fields must map to nulls so the model-free code paths (bare greeting, parse
 * failure) and any legacy payload still deserialize.
 */
class LanguageFieldsDeserializationTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new ParameterNamesModule())
            .build();

    private IntentExtractionResult resultFrom(String json) throws Exception {
        return mapper.readValue(json, IntentExtractionResult.class);
    }

    @Test
    void turkishHotelRequestDeserializesLanguageFields() throws Exception {
        IntentExtractionResult result = resultFrom(
                "{\"intent\":\"HOTEL\",\"criteria\":{\"location\":\"Antalya\",\"adults\":2},"
                        + "\"detectedLanguage\":\"tr\",\"languageConfidence\":\"HIGH\"}");

        assertThat(result.detectedLanguage()).isEqualTo("tr");
        assertThat(result.languageConfidence()).isEqualTo(LanguageConfidence.HIGH);
        assertThat(result.criteria().location()).isEqualTo("Antalya");
    }

    @Test
    void englishRequestDeserializesEnglishLanguage() throws Exception {
        IntentExtractionResult result = resultFrom(
                "{\"intent\":\"HOTEL\",\"criteria\":{\"location\":\"Antalya\",\"adults\":2},"
                        + "\"detectedLanguage\":\"en\",\"languageConfidence\":\"HIGH\"}");

        assertThat(result.detectedLanguage()).isEqualTo("en");
        assertThat(result.languageConfidence()).isEqualTo(LanguageConfidence.HIGH);
    }

    @Test
    void lowConfidenceShortMessageDeserializesLow() throws Exception {
        IntentExtractionResult result = resultFrom(
                "{\"intent\":\"HOTEL\",\"criteria\":{\"nights\":5},"
                        + "\"detectedLanguage\":\"en\",\"languageConfidence\":\"LOW\"}");

        assertThat(result.languageConfidence()).isEqualTo(LanguageConfidence.LOW);
    }

    @Test
    void payloadWithoutLanguageFieldsDeserializesToNulls() throws Exception {
        IntentExtractionResult result = resultFrom(
                "{\"intent\":\"OTHER\",\"criteria\":null}");

        assertThat(result.detectedLanguage()).isNull();
        assertThat(result.languageConfidence()).isNull();
        assertThat(result.criteria()).isNull();
    }
}
