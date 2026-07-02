package com.paximum.paxassist.hotel;

import java.util.List;

public interface TourVisioHotelApiClient {
    List<HotelProduct> searchHotels(HotelSearchCriteria criteria);
}
