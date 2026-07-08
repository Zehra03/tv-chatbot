package com.paximum.paxassist.reservation.infrastructure.tourvisio.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.paximum.paxassist.flight.infrastructure.client.TourVisioTokenProvider;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.config.TourVisioBookingClientConfig;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.AddServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithOfferRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithReservationRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.CancelReservationRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.CommitTransactionRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.GetCancellationPenaltyRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.GetReservationDetailRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.GetReservationListRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.RemoveServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.SetReservationInfoRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelReservationResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancellationPenaltyResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CommitTransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.RawTourVisioResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.BusinessFailure;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.Success;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.TechnicalFailure;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.UnknownOutcome;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.support.TourVisioAmbiguousException;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.support.TourVisioTechnicalException;

/**
 * {@link RestClient}-based implementation of {@link TourVisioBookingClient}.
 *
 * <p><b>Auth reuse:</b> the TourVisio Bearer token is obtained from the Flight module's
 * existing {@link TourVisioTokenProvider} (login + caching), not re-implemented here.
 * See the decisions note for the cross-module coupling this introduces.
 *
 * <p><b>Security:</b> request/response bodies (which carry passenger PII on
 * {@code setReservationInfo}) and the {@code Authorization} token are never logged — only
 * the operation name, HTTP status category, and non-PII {@code header.messages[].code}.
 */
@Component
public class RestClientTourVisioBookingClient implements TourVisioBookingClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientTourVisioBookingClient.class);

    // Endpoint paths. CONFIRMED convention: lowercase /api/bookingservice/<operation> (not PascalCase).
    // The configured base URL (tourvisio.url) already ends in /v2/api, and RestClient resolves
    // base + path, so we append "/bookingservice/<op>" (NOT "/api/bookingservice/<op>", which would
    // double the /api). Resolved e.g.: https://.../v2/api/bookingservice/begintransaction.
    private static final String BEGIN_TRANSACTION = "/bookingservice/begintransaction";
    private static final String ADD_SERVICES = "/bookingservice/addservices";
    private static final String REMOVE_SERVICES = "/bookingservice/removeservices";
    private static final String SET_RESERVATION_INFO = "/bookingservice/setreservationinfo";
    private static final String COMMIT_TRANSACTION = "/bookingservice/committransaction";
    private static final String GET_RESERVATION_DETAIL = "/bookingservice/getreservationdetail";
    private static final String GET_RESERVATION_LIST = "/bookingservice/getreservationlist";
    private static final String GET_CANCELLATION_PENALTY = "/bookingservice/getcancellationpenalty";
    private static final String CANCEL_RESERVATION = "/bookingservice/cancelreservation";

    /** Retryable, idempotent/basket-building call: safe to retry on an ambiguous timeout. */
    private static final CallMode RETRYABLE = new CallMode(true, false);
    /** Non-idempotent point-of-no-return: never auto-retry; ambiguous -> UnknownOutcome. */
    private static final CallMode POINT_OF_NO_RETURN = new CallMode(false, true);

    private final RestClient restClient;
    private final TourVisioTokenProvider tokenProvider;
    private final int maxAttempts;

    public RestClientTourVisioBookingClient(
            @Qualifier(TourVisioBookingClientConfig.BOOKING_REST_CLIENT) RestClient restClient,
            TourVisioTokenProvider tokenProvider,
            @Value("${tourvisio.booking.max-attempts:3}") int maxAttempts) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    // --- Basket-building steps ---------------------------------------------------------------

    @Override
    public TourVisioCallResult<TransactionResponse> beginTransactionWithOffer(BeginTransactionWithOfferRequest request) {
        return execute("beginTransactionWithOffer", BEGIN_TRANSACTION, request, TransactionResponse.class, RETRYABLE, null);
    }

    @Override
    public TourVisioCallResult<TransactionResponse> beginTransactionWithReservation(BeginTransactionWithReservationRequest request) {
        return execute("beginTransactionWithReservation", BEGIN_TRANSACTION, request, TransactionResponse.class, RETRYABLE,
                request == null ? null : request.reservationNumber());
    }

    @Override
    public TourVisioCallResult<TransactionResponse> addServices(AddServicesRequest request) {
        return execute("addServices", ADD_SERVICES, request, TransactionResponse.class, RETRYABLE, null);
    }

    @Override
    public TourVisioCallResult<TransactionResponse> removeServices(RemoveServicesRequest request) {
        return execute("removeServices", REMOVE_SERVICES, request, TransactionResponse.class, RETRYABLE, null);
    }

    @Override
    public TourVisioCallResult<TransactionResponse> setReservationInfo(SetReservationInfoRequest request) {
        return execute("setReservationInfo", SET_RESERVATION_INFO, request, TransactionResponse.class, RETRYABLE, null);
    }

    // --- Point of no return ------------------------------------------------------------------

    @Override
    public TourVisioCallResult<CommitTransactionResponse> commitTransaction(CommitTransactionRequest request) {
        // Non-idempotent purchase: no auto-retry. transactionId is the only hint we can offer the
        // caller for a follow-up getReservationDetail verification.
        String ref = request == null ? null : request.transactionId();
        return execute("commitTransaction", COMMIT_TRANSACTION, request, CommitTransactionResponse.class, POINT_OF_NO_RETURN, ref);
    }

    // --- Read-only lookups -------------------------------------------------------------------

    @Override
    public TourVisioCallResult<RawTourVisioResponse> getReservationDetail(String reservationNumber) {
        GetReservationDetailRequest request = new GetReservationDetailRequest(reservationNumber);
        return execute("getReservationDetail", GET_RESERVATION_DETAIL, request, RawTourVisioResponse.class, RETRYABLE, reservationNumber);
    }

    @Override
    public TourVisioCallResult<RawTourVisioResponse> getReservationList() {
        return execute("getReservationList", GET_RESERVATION_LIST, GetReservationListRequest.empty(), RawTourVisioResponse.class, RETRYABLE, null);
    }

    // --- Cancellation ------------------------------------------------------------------------

    @Override
    public TourVisioCallResult<CancellationPenaltyResponse> getCancellationPenalty(String reservationNumber) {
        GetCancellationPenaltyRequest request = new GetCancellationPenaltyRequest(reservationNumber);
        return execute("getCancellationPenalty", GET_CANCELLATION_PENALTY, request, CancellationPenaltyResponse.class, RETRYABLE, reservationNumber);
    }

    @Override
    public TourVisioCallResult<CancelReservationResponse> cancelReservation(String reservationNumber, String reason, List<String> serviceIds) {
        CancelReservationRequest request = new CancelReservationRequest(reservationNumber, reason, serviceIds);
        // Non-idempotent: no auto-retry; ambiguous failure -> UnknownOutcome keyed by reservationNumber.
        return execute("cancelReservation", CANCEL_RESERVATION, request, CancelReservationResponse.class, POINT_OF_NO_RETURN, reservationNumber);
    }

    // --- Core call + result mapping ----------------------------------------------------------

    private <T extends TourVisioResponse> TourVisioCallResult<T> execute(
            String operation, String path, Object requestBody, Class<T> responseType,
            CallMode mode, String reservationRef) {
        try {
            T response = mode.retryOnTimeout()
                    ? doPostWithRetry(operation, path, requestBody, responseType)
                    : doPost(path, requestBody, responseType);

            if (response == null || response.header() == null) {
                log.warn("TourVisio {} returned an empty or headerless response", operation);
                return new TechnicalFailure<>("Empty or headerless response from " + operation, null);
            }
            if (!response.header().success()) {
                log.info("TourVisio {} business failure: code={}", operation, response.header().primaryCode());
                return new BusinessFailure<>(response.header());
            }
            return new Success<>(response);

        } catch (TourVisioAmbiguousException e) {
            if (mode.nonIdempotent()) {
                log.error("TourVisio {} AMBIGUOUS outcome (no auto-retry) — verify via getReservationDetail before assuming failure: {}",
                        operation, e.getMessage());
                return new UnknownOutcome<>(reservationRef,
                        "Ambiguous failure on " + operation + "; verify via getReservationDetail before assuming failure",
                        e.getCause());
            }
            log.warn("TourVisio {} technical failure after {} attempt(s): {}", operation, maxAttempts, e.getMessage());
            return new TechnicalFailure<>("Ambiguous/technical failure on " + operation, e.getCause());

        } catch (TourVisioTechnicalException e) {
            log.warn("TourVisio {} technical failure: {}", operation, e.getMessage());
            return new TechnicalFailure<>(e.getMessage(), e.getCause());
        }
    }

    /** Retries only on {@link TourVisioAmbiguousException}; a definite technical failure is not retried. */
    private <T> T doPostWithRetry(String operation, String path, Object body, Class<T> type) {
        for (int attempt = 1; ; attempt++) {
            try {
                return doPost(path, body, type);
            } catch (TourVisioAmbiguousException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("Retrying TourVisio {} after ambiguous failure (attempt {}/{})", operation, attempt, maxAttempts);
            }
        }
    }

    private <T> T doPost(String path, Object body, Class<T> type) {
        try {
            return authorizedPost(path, body, type);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            // A 401 means the request was rejected at auth and NOT processed, so re-login + one
            // retry is safe even for non-idempotent calls (no side effect could have occurred).
            tokenProvider.invalidate();
            try {
                return authorizedPost(path, body, type);
            } catch (HttpClientErrorException.Unauthorized stillUnauthorized) {
                throw new TourVisioTechnicalException("Authorization rejected after re-login", stillUnauthorized);
            }
        }
    }

    private <T> T authorizedPost(String path, Object body, Class<T> type) {
        try {
            return restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw e; // handled in doPost (re-auth)
        } catch (HttpServerErrorException e) {
            // 5xx: TourVisio may have processed the request before failing → ambiguous
            throw new TourVisioAmbiguousException("TourVisio returned " + e.getStatusCode() + " for " + path, e);
        } catch (HttpClientErrorException e) {
            // other 4xx: request rejected before taking effect → definite technical failure
            throw new TourVisioTechnicalException("TourVisio returned " + e.getStatusCode() + " for " + path, e);
        } catch (ResourceAccessException e) {
            // connect/read timeout or dropped connection → ambiguous
            throw new TourVisioAmbiguousException("I/O error or timeout calling " + path, e);
        }
    }

    /**
     * @param retryOnTimeout retry on an ambiguous (timeout/5xx/network) failure
     * @param nonIdempotent  a point-of-no-return call whose ambiguous failure must surface as UnknownOutcome
     */
    private record CallMode(boolean retryOnTimeout, boolean nonIdempotent) {
    }
}
