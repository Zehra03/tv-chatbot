package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

public interface HotelSearchService {
    HotelSearchResponse searchHotels(HotelSearchRequest request);
}
