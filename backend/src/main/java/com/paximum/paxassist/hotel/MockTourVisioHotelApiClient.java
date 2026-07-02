package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Profile("mock")
public class MockTourVisioHotelApiClient implements TourVisioHotelApiClient {

    @Override
    public List<HotelProduct> searchHotels(HotelSearchCriteria criteria) {
        return List.of(
            new HotelProduct("H1", "Rixos Premium", "Antalya", 5, new BigDecimal("1500.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H2", "Titanic Mardan Palace", "Antalya", 5, new BigDecimal("2000.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H3", "Kaya Palazzo", "Belek", 5, new BigDecimal("1800.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H4", "Maxx Royal", "Kemer", 5, new BigDecimal("3000.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H5", "Hilton Bosphorus", "Istanbul", 5, new BigDecimal("350.00"), "EUR", "Bed & Breakfast", true),
            new HotelProduct("H6", "Swissotel The Bosphorus", "Istanbul", 5, new BigDecimal("400.00"), "EUR", "Bed & Breakfast", true)
        );
    }

    @Override
    public String authenticate() {
        return "mock-token";
    }

    @Override
    public AutocompleteResponse getArrivalAutocomplete(String query) {
        return new AutocompleteResponse(
            new AutocompleteResponse.Header(true),
            new AutocompleteResponse.Body(List.of(
                new AutocompleteResponse.Item(
                    1,
                    new AutocompleteResponse.City("23494", "Antalya"),
                    null
                )
            ))
        );
    }

    @Override
    public Object priceSearch(HotelSearchRequest criteria, String locationId) {
        return List.of();
    }
}
