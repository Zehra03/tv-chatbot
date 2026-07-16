package com.paximum.paxassist.hotel;

import java.util.List;

import com.paximum.paxassist.hotel.dto.HotelLocationDto;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

public interface HotelSearchService {
    HotelSearchResponse searchHotels(HotelSearchRequest request);

    /**
     * Destination autocomplete for the search form: turns the free text a user types ("Ant") into
     * the TourVisio place names available for hotels ("Antalya"), so they pick a real location
     * instead of guessing. Backed by the same {@code getArrivalAutocomplete} the search itself uses;
     * only named city suggestions are returned. Never throws — a provider failure yields an empty
     * list so the dropdown degrades quietly.
     */
    List<HotelLocationDto> suggestLocations(String query);

    /**
     * Probes availability for check-in dates near {@code base}'s check-in and returns the ones
     * (ISO {@code yyyy-MM-dd}) that actually have at least one available hotel. Backs the chat
     * "başka hangi tarihte müsait?" flow: the destination location is resolved once, then each
     * candidate date ({@code base.checkIn + stepDays·k}, k=1..) is price-searched until
     * {@code maxResults} available dates are found or {@code windowDays} is exhausted.
     *
     * <p>Only dates TourVisio confirms as available are returned — never fabricated. An empty list
     * means no availability was found in the probed window (or the destination could not be
     * resolved, or {@code base} lacks destination/night/adult).
     *
     * @param base        the failed search's criteria (destination, night, adult, currency, …);
     *                    its {@code checkIn} is the anchor the probe steps forward from (today+1
     *                    when absent)
     * @param stepDays    spacing between probed candidate dates (must be &gt; 0)
     * @param windowDays  how many days ahead of the anchor to probe at most
     * @param maxResults  stop once this many available dates are collected
     */
    List<String> suggestAvailableCheckInDates(HotelSearchRequest base, int stepDays, int windowDays, int maxResults);
}
