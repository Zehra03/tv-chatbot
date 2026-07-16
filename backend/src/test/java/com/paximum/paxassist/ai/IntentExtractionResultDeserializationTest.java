package com.paximum.paxassist.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Verifies the wire contract between the LLM and the backend for the flight-filter slots: the JSON
 * the intent model is prompted to emit must deserialize into {@link IntentExtractionResult} /
 * {@link SlotCriteria} with the fields populated — this is the boundary where the extracted data
 * used to be lost. The mapper mirrors how Spring AI's converter reads records (canonical
 * constructor + parameter names), so a green test here means the model's output survives into
 * {@code SlotCriteria}.
 */
class IntentExtractionResultDeserializationTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new ParameterNamesModule())
            .build();

    private SlotCriteria criteriaFrom(String json) throws Exception {
        IntentExtractionResult result = mapper.readValue(json, IntentExtractionResult.class);
        assertThat(result.criteria()).isNotNull();
        return result.criteria();
    }

    @Test
    void nonstopRequestDeserializesDirectFlightTrue() throws Exception {
        SlotCriteria criteria = criteriaFrom(
                "{\"intent\":\"FILTER\",\"criteria\":{\"directFlight\":true}}");

        assertThat(criteria.directFlight()).isTrue();
    }

    @Test
    void airlineRequestDeserializesAirline() throws Exception {
        SlotCriteria criteria = criteriaFrom(
                "{\"intent\":\"FILTER\",\"criteria\":{\"airline\":\"THY\"}}");

        assertThat(criteria.airline()).isEqualTo("THY");
    }

    @Test
    void morningRequestDeserializesDepartTimeRange() throws Exception {
        SlotCriteria criteria = criteriaFrom(
                "{\"intent\":\"FILTER\",\"criteria\":{\"departTimeRange\":\"morning\"}}");

        assertThat(criteria.departTimeRange()).isEqualTo("morning");
    }

    @Test
    void combinedFlightRequestDeserializesAllFlightFilterSlots() throws Exception {
        SlotCriteria criteria = criteriaFrom(
                "{\"intent\":\"FLIGHT\",\"criteria\":{\"origin\":\"İstanbul\",\"destination\":\"İzmir\","
                        + "\"airline\":\"THY\",\"departTimeRange\":\"morning\",\"directFlight\":true}}");

        assertThat(criteria.origin()).isEqualTo("İstanbul");
        assertThat(criteria.destination()).isEqualTo("İzmir");
        assertThat(criteria.airline()).isEqualTo("THY");
        assertThat(criteria.departTimeRange()).isEqualTo("morning");
        assertThat(criteria.directFlight()).isTrue();
    }
}
