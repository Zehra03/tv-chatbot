package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import java.util.List;

public interface TourVisioHotelApiClient {
    List<HotelProduct> searchHotels(HotelSearchCriteria criteria);

    String authenticate();
    AutocompleteResponse getArrivalAutocomplete(String query);
    Object priceSearch(HotelSearchRequest criteria, String locationId);

    /**
     * Fetches the full product detail for one hotel (TourVisio GetProductInfo) — the payload the
     * detail screen's feature model ({@code HotelFeatureDetails}) is built from. Called only when a
     * user opens a single hotel, so the per-hotel cost is paid once (the listing screen never calls
     * this — that would be an N+1 across the whole result set). Returns the raw provider payload;
     * {@code HotelFeatureMapper} turns it into the response model.
     *
     * @param productId     the TourVisio hotel/product id (the {@code id} on the search card).
     * @param ownerProvider the hotel's owner provider ({@code HotelProduct#provider()}); TourVisio
     *                      GetProductInfo REQUIRES it (a null/absent value fails with
     *                      "pSource Parameter can not be null").
     */
    Object getProductInfo(String productId, Integer ownerProvider);
}
