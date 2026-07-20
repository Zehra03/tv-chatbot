package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;
import com.paximum.paxassist.hotel.facility.BoardNormalizer;
import com.paximum.paxassist.hotel.facility.HotelFeatureMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the detail-screen feature model for a single hotel: fetches the full TourVisio product
 * detail ({@code GetProductInfo}) and maps it into the standard {@link HotelFeatureDetails}. Kept out
 * of the controller so the controller stays thin (project guardrail) and the search path is untouched.
 *
 * <p>Detail-screen only — invoked when a user opens one hotel, so the per-hotel provider call is made
 * once, never once-per-result on the listing screen.
 */
@Service
public class HotelDetailsService {

    private final TourVisioHotelApiClient tourVisioHotelApiClient;
    private final HotelFeatureMapper hotelFeatureMapper;

    public HotelDetailsService(TourVisioHotelApiClient tourVisioHotelApiClient,
                               HotelFeatureMapper hotelFeatureMapper) {
        this.tourVisioHotelApiClient = tourVisioHotelApiClient;
        this.hotelFeatureMapper = hotelFeatureMapper;
    }

    /**
     * @param productId     the TourVisio hotel/product id (the {@code id} carried on the search cards).
     * @param ownerProvider the hotel's owner provider ({@code HotelProduct#provider()}); required by
     *                      TourVisio GetProductInfo (see {@link TourVisioHotelApiClient#getProductInfo}).
     * @param boardType     the board name the search card already carried ({@code HotelProduct#boardType()}),
     *                      passed back by the frontend. GetProductInfo does NOT return board data
     *                      (board is offer-level), so we normalize this instead of making an extra
     *                      GetOfferDetails call. Null/blank → {@code boardOptions} stays empty.
     * @return the normalized feature model. Missing facility/board/theme sections yield empty parts,
     *         never an error.
     */
    public HotelFeatureDetails getFeatureDetails(String productId, Integer ownerProvider, String boardType) {
        Object rawDetail = tourVisioHotelApiClient.getProductInfo(productId, ownerProvider);
        HotelFeatureDetails details = hotelFeatureMapper.map(rawDetail);

        // GetProductInfo carries no boardName, so the mapper's boardOptions is empty. Fill it from the
        // board the search card already knew (no second provider call). Only overlay when the mapper
        // found nothing, so a future GetOfferDetails-sourced board would still win.
        if (details.boardOptions().isEmpty() && boardType != null && !boardType.isBlank()) {
            List<String> boardOptions = BoardNormalizer.normalizeAll(List.of(boardType));
            return new HotelFeatureDetails(details.hotelFeatures(), boardOptions, details.themeFilters());
        }
        return details;
    }
}
