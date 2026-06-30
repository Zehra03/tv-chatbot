package com.paximum.paxassist.hotel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    private final TourVisioHotelApiClient hotelApiClient;

    public HotelController(TourVisioHotelApiClient hotelApiClient) {
        this.hotelApiClient = hotelApiClient;
    }

    @GetMapping
    public List<HotelProduct> getHotels(@RequestParam(defaultValue = "Antalya") String destination) {
        return hotelApiClient.searchHotels(new HotelSearchCriteria(destination));
    }
}
