package com.paximum.paxassist.reservation.infrastructure.tourvisio.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.paximum.paxassist.flight.infrastructure.client.TourVisioTokenProvider;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithOfferRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelReservationResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult;

@ExtendWith(MockitoExtension.class)
class RestClientTourVisioBookingClientTest {

    private RestClientTourVisioBookingClient client;
    private MockRestServiceServer server;

    @Mock
    private TourVisioTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        when(tokenProvider.getToken()).thenReturn("mock-token");

        RestClient.Builder builder = RestClient.builder().baseUrl("https://mock.tourvisio.com/v2/api");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        client = new RestClientTourVisioBookingClient(restClient, tokenProvider, 1);
    }

    @Test
    void beginTransactionWithOffer_success_mapsResponse() {
        // Given
        String responseJson = """
                {
                    "header": {
                        "success": true,
                        "messages": []
                    },
                    "body": {
                        "transactionId": "txn-123",
                        "expiresOn": "2026-07-08T12:00:00Z"
                    }
                }
                """;

        server.expect(requestTo("https://mock.tourvisio.com/v2/api/bookingservice/begintransaction"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-token"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // When
        BeginTransactionWithOfferRequest request = new BeginTransactionWithOfferRequest(List.of("offer-1"), "EUR", "en-US");
        TourVisioCallResult<TransactionResponse> result = client.beginTransactionWithOffer(request);

        // Then
        assertThat(result).isInstanceOf(TourVisioCallResult.Success.class);
        TourVisioCallResult.Success<TransactionResponse> success = (TourVisioCallResult.Success<TransactionResponse>) result;
        assertThat(success.body().header().success()).isTrue();
        assertThat(success.body().body().transactionId()).isEqualTo("txn-123");
    }

    @Test
    void cancelReservation_businessFailure_mapsToBusinessFailure() {
        // Given
        String responseJson = """
                {
                    "header": {
                        "success": false,
                        "messages": [
                            { "id": 1, "code": "ERR-123", "messageType": 2, "message": "Cannot cancel" }
                        ]
                    },
                    "body": null
                }
                """;

        server.expect(requestTo("https://mock.tourvisio.com/v2/api/bookingservice/cancelreservation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // When
        TourVisioCallResult<CancelReservationResponse> result = client.cancelReservation("RES-123", "Reason", List.of());

        // Then
        assertThat(result).isInstanceOf(TourVisioCallResult.BusinessFailure.class);
        TourVisioCallResult.BusinessFailure<?> failure = (TourVisioCallResult.BusinessFailure<?>) result;
        assertThat(failure.header().success()).isFalse();
        assertThat(failure.header().messages()).hasSize(1);
        assertThat(failure.header().messages().get(0).code()).isEqualTo("ERR-123");
    }

    @Test
    void cancelReservation_serverError_mapsToUnknownOutcome() {
        // Given
        server.expect(requestTo("https://mock.tourvisio.com/v2/api/bookingservice/cancelreservation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        TourVisioCallResult<CancelReservationResponse> result = client.cancelReservation("RES-123", "Reason", List.of());

        // Then (Because cancel is a point-of-no-return, a 5xx becomes UnknownOutcome)
        assertThat(result).isInstanceOf(TourVisioCallResult.UnknownOutcome.class);
        TourVisioCallResult.UnknownOutcome<?> unknown = (TourVisioCallResult.UnknownOutcome<?>) result;
        assertThat(unknown.reservationRef()).isEqualTo("RES-123");
    }
}
