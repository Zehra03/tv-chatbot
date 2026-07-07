package com.paximum.paxassist.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

/**
 * Model Context Protocol tools exposing the SEARCH use-cases. Each tool is a thin delegate over the
 * real {@link HotelSearchService} / {@link FlightSearchService}, so results come only from TourVisio —
 * nothing is fabricated. Missing required fields surface as the services' INCOMPLETE outcome, not an
 * error.
 *
 * <p><b>Security boundary (least privilege):</b> only search tools live here. There is deliberately
 * NO reservation/booking tool — the "chatbot never books" invariant holds on the MCP surface too.
 * The MCP endpoints inherit the app's JWT security (authenticated by default); do not permit them
 * publicly without an access control in front, since they bypass the chat guard.
 */
@Component
public class HotelFlightTools {

    private final HotelSearchService hotelSearchService;
    private final FlightSearchService flightSearchService;

    public HotelFlightTools(HotelSearchService hotelSearchService, FlightSearchService flightSearchService) {
        this.hotelSearchService = hotelSearchService;
        this.flightSearchService = flightSearchService;
    }

    @Tool(description = "Search real-time hotel availability and prices from TourVisio. "
            + "Returns live results only; never invents hotels, prices or availability. "
            + "If required fields are missing the response status is INCOMPLETE with the missing field names.")
    public HotelSearchResponse searchHotels(
            @ToolParam(description = "Destination city or region, e.g. \"Antalya\"") String destination,
            @ToolParam(description = "Check-in date, format YYYY-MM-DD") String checkIn,
            @ToolParam(description = "Number of nights to stay") Integer night,
            @ToolParam(description = "Number of adult guests") Integer adult,
            @ToolParam(description = "Ages of accompanying children; empty when none", required = false) List<Integer> childAges,
            @ToolParam(description = "Guest nationality, ISO 3166 alpha-2 (default TR)", required = false) String nationality,
            @ToolParam(description = "Price currency, ISO 4217 (default TRY)", required = false) String currency) {

        HotelSearchRequest request = new HotelSearchRequest(
                destination, checkIn, night, adult, childAges, nationality, currency, null);
        return hotelSearchService.searchHotels(request);
    }

    @Tool(description = "Search real-time flight availability and prices from TourVisio. "
            + "Returns live results only; never invents flights or prices. "
            + "A one-way trip is assumed unless a return date is given.")
    public FlightSearchOutcome searchFlights(
            @ToolParam(description = "Origin city or airport, e.g. \"IST\"") String origin,
            @ToolParam(description = "Destination city or airport, e.g. \"CDG\"") String destination,
            @ToolParam(description = "Departure date, format YYYY-MM-DD") String departureDate,
            @ToolParam(description = "Return date YYYY-MM-DD; omit for one-way", required = false) String returnDate,
            @ToolParam(description = "Number of adult passengers") Integer adults,
            @ToolParam(description = "Number of child passengers", required = false) Integer children,
            @ToolParam(description = "Price currency, ISO 4217, e.g. \"TRY\"") String currency) {

        LocalDate depart = parse(departureDate);
        LocalDate ret = parse(returnDate);
        PassengerCount passengers = (adults == null) ? null : PassengerCount.builder()
                .adults(adults)
                .children(children != null ? children : 0)
                .infants(0)
                .build();

        FlightSearchCriteria criteria = FlightSearchCriteria.builder()
                .origin(origin)
                .destination(destination)
                .departDate(depart)
                .returnDate(ret)
                .tripType(ret != null ? TripType.ROUND_TRIP : TripType.ONE_WAY)
                .passengers(passengers)
                .currency(currency)
                .build();
        return flightSearchService.search(criteria);
    }

    private LocalDate parse(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
