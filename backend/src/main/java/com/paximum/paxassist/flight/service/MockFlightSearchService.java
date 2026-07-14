package com.paximum.paxassist.flight.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.TripType;

/**
 * Static flight data for offline runs — active under {@code mock}/{@code demo}, so flight search
 * (endpoint + chat FLIGHT handler) returns real cards without TourVisio credentials. Cards echo the
 * requested origin/destination/date/currency and trip type. The real path is
 * {@link TourVisioFlightSearchService} ({@code @Profile("!mock & !demo")}).
 */
@Service
@Profile("mock | demo")
public class MockFlightSearchService implements FlightSearchService {

    @Override
    public FlightSearchOutcome search(FlightSearchCriteria criteria) {
        List<String> missing = criteria.missingRequiredFields();
        if (!missing.isEmpty()) {
            return FlightSearchOutcome.incomplete(missing);
        }
        String cur = criteria.getCurrency();
        List<FlightProduct> products = List.of(
                build(criteria, "FLT-1", "Turkish Airlines", "TK1980", 8, 4, 0, "20kg", "2500.00", cur),
                build(criteria, "FLT-2", "Pegasus", "PC2110", 12, 5, 1, "15kg", "1450.00", cur),
                build(criteria, "FLT-3", "SunExpress", "XQ640", 18, 4, 0, "20kg", "1980.00", cur));
        // The fixture's departure hours are built at UTC (see at()), so the window is read there too.
        return FlightSearchOutcome.complete(FlightResultFilter.apply(criteria, products, ZoneOffset.UTC));
    }

    private FlightProduct build(FlightSearchCriteria c, String id, String airline, String flightNumber,
                                int departHour, int durationHours, int stops, String baggage,
                                String price, String cur) {
        Instant depart = at(c.getDepartDate(), departHour);
        Instant arrive = depart.plusSeconds(durationHours * 3600L);
        boolean roundTrip = c.getTripType() == TripType.ROUND_TRIP && c.getReturnDate() != null;
        Instant returnDepart = roundTrip ? at(c.getReturnDate(), departHour) : null;
        Instant returnArrive = roundTrip ? returnDepart.plusSeconds(durationHours * 3600L) : null;
        return FlightProduct.builder()
                .id(id)
                .airline(airline)
                .flightNumber(flightNumber)
                .origin(c.getOrigin())
                .destination(c.getDestination())
                .departTime(depart)
                .arriveTime(arrive)
                .returnDepartTime(returnDepart)
                .returnArriveTime(returnArrive)
                .stops(stops)
                .durationMinutes(durationHours * 60)
                .baggage(baggage)
                .price(new BigDecimal(price))
                .currency(cur)
                .build();
    }

    private Instant at(LocalDate date, int hour) {
        return date.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC);
    }
}
