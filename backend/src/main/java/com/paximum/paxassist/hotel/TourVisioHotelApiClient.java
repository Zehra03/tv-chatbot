package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import java.util.List;

public interface TourVisioHotelApiClient {
    List<HotelProduct> searchHotels(HotelSearchCriteria criteria);

    String authenticate();
    AutocompleteResponse getArrivalAutocomplete(String query);
    Object priceSearch(HotelSearchRequest criteria, String locationId);
}
