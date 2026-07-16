package com.paximum.paxassist.flight.infrastructure.mapper;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioLocationRequest;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPassengerRequest;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;

@Component
public class TourVisioFlightRequestMapper {

    private static final int PRODUCT_TYPE_FLIGHT = 3;
    private static final int LOCATION_TYPE_DEPARTURE = 2;
    private static final int LOCATION_TYPE_ARRIVAL = 5;
    private static final int PASSENGER_TYPE_ADULT = 1;
    private static final int PASSENGER_TYPE_CHILD = 2;
    private static final int PASSENGER_TYPE_INFANT = 3;
    private static final DateTimeFormatter CHECK_IN_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Response shape 3: outbound and return come as separate results whose offers carry group keys
     * and a booking token per key — the only shape that lets a return be paired with an outbound.
     */
    private static final List<Integer> GROUPED_RESPONSE_LIST_TYPE = List.of(3);

    private final TourVisioProperties tourVisioProperties;

    public TourVisioFlightRequestMapper(TourVisioProperties tourVisioProperties) {
        this.tourVisioProperties = tourVisioProperties;
    }

    public TourVisioPriceSearchRequest toRequest(FlightSearchCriteria criteria) {
        boolean roundTrip = criteria.getTripType() == TripType.ROUND_TRIP;
        if (roundTrip && criteria.getReturnDate() == null) {
            throw new IllegalArgumentException("returnDate is required for ROUND_TRIP searches");
        }
        Integer night = null;
        if (roundTrip) {
            night = (int) ChronoUnit.DAYS.between(criteria.getDepartDate(), criteria.getReturnDate());
            if (night < 0) {
                throw new IllegalArgumentException(
                        "returnDate must be on or after departDate for ROUND_TRIP searches");
            }
        }

        return new TourVisioPriceSearchRequest(
                PRODUCT_TYPE_FLIGHT,
                List.of(roundTrip ? "2" : "1"),
                criteria.getDepartDate().format(CHECK_IN_FORMAT),
                night,
                List.of(new TourVisioLocationRequest(criteria.getOrigin(), LOCATION_TYPE_DEPARTURE)),
                List.of(new TourVisioLocationRequest(criteria.getDestination(), LOCATION_TYPE_ARRIVAL)),
                toPassengers(criteria.getPassengers()),
                GROUPED_RESPONSE_LIST_TYPE,
                tourVisioProperties.culture(),
                criteria.getCurrency());
    }

    private List<TourVisioPassengerRequest> toPassengers(PassengerCount passengers) {
        List<TourVisioPassengerRequest> result = new ArrayList<>();
        if (passengers.getAdults() > 0) {
            result.add(new TourVisioPassengerRequest(PASSENGER_TYPE_ADULT, passengers.getAdults()));
        }
        if (passengers.getChildren() > 0) {
            result.add(new TourVisioPassengerRequest(PASSENGER_TYPE_CHILD, passengers.getChildren()));
        }
        if (passengers.getInfants() > 0) {
            result.add(new TourVisioPassengerRequest(PASSENGER_TYPE_INFANT, passengers.getInfants()));
        }
        return result;
    }
}